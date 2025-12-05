package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.PagePreviewInfo
import eu.kanade.tachiyomi.source.PagePreviewPage
import eu.kanade.tachiyomi.source.PagePreviewSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import exh.metadata.MetadataUtil
import exh.metadata.metadata.LanraragiSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds

class Lanraragi(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<LanraragiSearchMetadata, Response>,
    NamespaceSource,
    PagePreviewSource {
    override val metaClass = LanraragiSearchMetadata::class
    override fun newMetaInstance() = LanraragiSearchMetadata()
    override val lang = delegate.lang

    private fun getApiUriBuilder(path: String): Uri.Builder {
        return LanraragiSearchMetadata.getApiUriBuilder(baseUrl, path)
    }

    private fun getReaderId(url: String): String {
        return READER_ID_REGEX.find(url)?.groupValues?.get(1) ?: ""
    }

    private fun getThumbnailId(url: String): String {
        return THUMBNAIL_ID_REGEX.find(url)?.groupValues?.get(1) ?: ""
    }

    // Helper
    private suspend fun getRandomID(query: String): String {
        val searchRandom = client.newCall(GET("$baseUrl/api/search/random?count=1&$query", headers)).awaitSuccess()
        val data = jsonParser.parseToJsonElement(searchRandom.body.string()).jsonObject["data"]
        val archive = data!!.jsonArray.firstOrNull()?.jsonObject

        // 0.8.2~0.8.7 = id, 0.8.8+ = arcid
        return (archive?.get("arcid") ?: archive?.get("id"))?.jsonPrimitive?.content ?: ""
    }

    private suspend fun customMangaDetailsRequest(manga: SManga): Request {
        val id = if (manga.url.startsWith("/api/search/random")) {
            getRandomID(manga.url.toUri().encodedQuery.toString())
        } else {
            getReaderId(manga.url)
        }
        val uri = getApiUriBuilder("/api/archives/$id/metadata").build()

        return GET(uri.toString(), headers)
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val response = client.newCall(customMangaDetailsRequest(manga)).awaitSuccess()
        return parseToManga(manga, response)
    }

    override suspend fun parseIntoMetadata(metadata: LanraragiSearchMetadata, input: Response) {
        val archive = with(jsonParser) { input.parseAs<Archive>() }

        with(metadata) {
            arcId = archive.arcid

            title = archive.title

            summary = archive.summary

            tags.clear()
            archive.tags?.split(',')
                ?.mapTo(tags) {
                    val tag = it.trim()
                    if (
                        tag.startsWith(LanraragiSearchMetadata.LANRARAGI_NAMESPACE_DATE_ADDED) ||
                        tag.startsWith(LanraragiSearchMetadata.LANRARAGI_NAMESPACE_TIMESTAMP)
                    ) {
                        val second = tag.substringAfter(':').trim().toLongOrNull()
                        if (second != null) {
                            val formattedTag = MetadataUtil.EX_DATE_FORMAT.withZone(ZoneOffset.UTC)
                                .format(Instant.ofEpochSecond(second))
                            RaisedTag(
                                tag.substringBefore(':'),
                                formattedTag,
                                LanraragiSearchMetadata.TAG_TYPE_DEFAULT,
                            )
                        } else {
                            RaisedTag(
                                tag.substringBefore(':'),
                                tag.substringAfter(':'),
                                LanraragiSearchMetadata.TAG_TYPE_DEFAULT,
                            )
                        }
                    } else {
                        RaisedTag(
                            tag.substringBefore(':', LanraragiSearchMetadata.LANRARAGI_NAMESPACE_OTHER),
                            tag.substringAfter(':'),
                            LanraragiSearchMetadata.TAG_TYPE_DEFAULT,
                        )
                    }
                }

            pageCount = archive.pagecount

            filename = archive.filename

            extension = archive.extension

            baseUrl = this@Lanraragi.baseUrl
        }
    }

    @Serializable
    data class Archive(
        val arcid: String,
        val isnew: String,
        val tags: String?,
        val summary: String?,
        val title: String,
        val pagecount: Int,
        val filename: String,
        val extension: String,
    )

    override suspend fun getPagePreviewList(manga: SManga, chapters: List<SChapter>, page: Int): PagePreviewPage {
        val metadata = fetchOrLoadMetadata(manga.id()) {
            client.newCall(customMangaDetailsRequest(manga)).awaitSuccess()
        }
        return PagePreviewPage(
            page,
            (1..(metadata.pageCount ?: 1)).map { index ->
                PagePreviewInfo(
                    index,
                    imageUrl = LanraragiSearchMetadata.getThumbnailUri(baseUrl, metadata.arcId!!, index),
                )
            },
            false,
            1,
        )
    }

    private suspend fun requestPreviewImage(page: PagePreviewInfo, cacheControl: CacheControl?): Response {
        return client.newCachelessCallWithProgress(
            if (cacheControl != null) {
                GET(page.imageUrl, cache = cacheControl, headers = headers)
            } else {
                GET(page.imageUrl, headers = headers)
            },
            page,
        ).awaitSuccess()
    }

    @Serializable
    data class ThumbnailTask(
        val job: Int,
        val operation: String,
        val success: Int,
    )

    @Serializable
    data class TaskProgress(
        val state: String,
    )

    suspend fun minionJobDone(jobId: Int): Boolean {
        return client.newCall(
            GET(
                getApiUriBuilder("/api/minion/$jobId").build().toString(),
                headers = headers,
            ),
        ).awaitSuccess().let {
            with(jsonParser) {
                it.parseAs<TaskProgress>().state == "finished"
            }
        }
    }

    override suspend fun fetchPreviewImage(page: PagePreviewInfo, cacheControl: CacheControl?): Response {
        return requestPreviewImage(page, cacheControl).let {
            if (it.code == 202) {
                val task = with(jsonParser) {
                    it.parseAs<ThumbnailTask>()
                }
                var tries = 0
                do {
                    if (tries > /* KMK --> */ 0 /* KMK <-- */) {
                        delay(200.milliseconds)
                    }
                    val jobDone = minionJobDone(task.job)
                } while (!jobDone && tries++ < 3)
                requestPreviewImage(page, cacheControl).apply {
                    if (code == 202) {
                        throw IOException("Thumbnail not ready")
                    }
                }
            } else {
                it
            }
        }
    }

    companion object {
        private val jsonParser = Json {
            ignoreUnknownKeys = true
        }

        private val READER_ID_REGEX = Regex("""/reader\?id=(\w{40})""")
        private val THUMBNAIL_ID_REGEX = Regex("""/(\w{40})/thumbnail""")
    }
}
