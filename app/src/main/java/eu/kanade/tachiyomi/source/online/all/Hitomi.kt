package eu.kanade.tachiyomi.source.online.all

import android.content.Context
import android.net.Uri
import android.os.Build
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.NamespaceSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.asJsoup
import exh.metadata.metadata.HitomiSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.DelegatedHttpSource
import exh.ui.metadata.adapters.HitomiDescriptionAdapter
import exh.util.urlImportFetchSearchManga
import org.jsoup.nodes.Document
import tachiyomi.source.model.MangaInfo
import java.text.SimpleDateFormat
import java.util.Locale

class Hitomi(delegate: HttpSource, val context: Context) :
    DelegatedHttpSource(delegate),
    MetadataSource<HitomiSearchMetadata, Document>,
    UrlImportableSource,
    NamespaceSource {
    override val metaClass = HitomiSearchMetadata::class
    override val lang = if (id == otherId) "all" else delegate.lang

    // Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        urlImportFetchSearchManga(context, query) {
            super.fetchSearchManga(page, query, filters)
        }

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        val response = client.newCall(mangaDetailsRequest(manga.toSManga())).await()
        return parseToManga(manga, response.asJsoup())
    }

    override suspend fun parseIntoMetadata(metadata: HitomiSearchMetadata, input: Document) {
        with(metadata) {
            url = input.location()

            tags.clear()

            thumbnailUrl = "https:" + input.selectFirst(".cover img").attr("src")

            val galleryElement = input.selectFirst(".gallery")

            title = galleryElement.selectFirst("h1").text()
            artists = galleryElement.select("h2 a").map { it.text() }
            tags += artists.map { RaisedTag("artist", it, RaisedSearchMetadata.TAG_TYPE_VIRTUAL) }

            input.select(".gallery-info tr").forEach { galleryInfoElement ->
                val content = galleryInfoElement.child(1)
                when (galleryInfoElement.child(0).text().toLowerCase()) {
                    "group" -> {
                        group = content.text()
                        tags += RaisedTag("group", group!!, RaisedSearchMetadata.TAG_TYPE_VIRTUAL)
                    }
                    "type" -> {
                        genre = content.text()
                        tags += RaisedTag("type", genre!!, RaisedSearchMetadata.TAG_TYPE_VIRTUAL)
                    }
                    "series" -> {
                        series = content.select("a").map { it.text() }
                        tags += series.map {
                            RaisedTag("series", it, RaisedSearchMetadata.TAG_TYPE_VIRTUAL)
                        }
                    }
                    "language" -> {
                        language = content.selectFirst("a")?.attr("href")?.split('-')?.get(1)
                        language?.let {
                            tags += RaisedTag("language", it, RaisedSearchMetadata.TAG_TYPE_VIRTUAL)
                        }
                    }
                    "characters" -> {
                        characters = content.select("a").map { it.text() }
                        tags += characters.map {
                            RaisedTag(
                                "character",
                                it,
                                HitomiSearchMetadata.TAG_TYPE_DEFAULT
                            )
                        }
                    }
                    "tags" -> {
                        tags += content.select("a").map {
                            val ns = when {
                                it.attr("href").startsWith("/tag/male") -> "male"
                                it.attr("href").startsWith("/tag/female") -> "female"
                                else -> "misc"
                            }
                            RaisedTag(
                                ns,
                                it.text().dropLast(if (ns == "misc") 0 else 2),
                                HitomiSearchMetadata.TAG_TYPE_DEFAULT
                            )
                        }
                    }
                }
            }

            uploadDate = try {
                DATE_FORMAT.parse(input.selectFirst(".gallery-info .date").text())!!.time
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun toString() = "$name (${lang.toUpperCase()})"

    override fun ensureDelegateCompatible() {
        if (versionId != delegate.versionId) {
            throw IncompatibleDelegateException("Delegate source is not compatible (versionId: $versionId <=> ${delegate.versionId})!")
        }
    }

    override val matchingHosts = listOf(
        "hitomi.la"
    )

    override suspend fun mapUrlToMangaUrl(uri: Uri): String? {
        val lcFirstPathSegment = uri.pathSegments.firstOrNull()?.toLowerCase() ?: return null

        if (lcFirstPathSegment != "manga" && lcFirstPathSegment != "reader") {
            return null
        }

        return "https://hitomi.la/manga/${uri.pathSegments[1].substringBefore('.')}.html"
    }

    override fun getDescriptionAdapter(controller: MangaController): HitomiDescriptionAdapter {
        return HitomiDescriptionAdapter(controller)
    }

    companion object {
        const val otherId = 2703068117101782422L
        private val DATE_FORMAT by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ssX", Locale.US)
            } else {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss'-05'", Locale.US)
            }
        }
    }
}
