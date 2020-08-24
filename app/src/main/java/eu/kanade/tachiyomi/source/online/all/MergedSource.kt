package eu.kanade.tachiyomi.source.online.all

import com.elvishew.xlog.XLog
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.SuspendHttpSource
import exh.MERGED_SOURCE_ID
import exh.util.asFlow
import exh.util.await
import exh.util.awaitSingle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

// TODO LocalSource compatibility
// TODO Disable clear database option
class MergedSource : SuspendHttpSource() {
    private val db: DatabaseHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val gson: Gson by injectLazy()

    override val id: Long = MERGED_SOURCE_ID

    override val baseUrl = ""

    override suspend fun popularMangaRequestSuspended(page: Int) = throw UnsupportedOperationException()
    override suspend fun popularMangaParseSuspended(response: Response) = throw UnsupportedOperationException()
    override suspend fun searchMangaRequestSuspended(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override suspend fun searchMangaParseSuspended(response: Response) = throw UnsupportedOperationException()
    override suspend fun latestUpdatesRequestSuspended(page: Int) = throw UnsupportedOperationException()
    override suspend fun latestUpdatesParseSuspended(response: Response) = throw UnsupportedOperationException()

    override suspend fun fetchMangaDetailsSuspended(manga: SManga): SManga {
        return readMangaConfig(manga).load(db, sourceManager).take(1).map { loaded ->
            SManga.create().apply {
                this.copyFrom(loaded.manga)
                url = manga.url
            }
        }.first()
    }

    override suspend fun fetchChapterListSuspended(manga: SManga): List<SChapter> {
        val loadedMangas = readMangaConfig(manga).load(db, sourceManager).buffer()
        return loadedMangas.flatMapMerge { loadedManga ->
            withContext(Dispatchers.IO) {
                loadedManga.source.fetchChapterList(loadedManga.manga).asFlow().map { chapterList ->
                    chapterList.map { chapter ->
                        chapter.apply {
                            url = writeUrlConfig(
                                UrlConfig(
                                    loadedManga.source.id,
                                    url,
                                    loadedManga.manga.url
                                )
                            )
                        }
                    }
                }
            }
        }.buffer().toList().flatten()
    }

    override suspend fun mangaDetailsParseSuspended(response: Response) = throw UnsupportedOperationException()
    override suspend fun chapterListParseSuspended(response: Response) = throw UnsupportedOperationException()

    override suspend fun fetchPageListSuspended(chapter: SChapter): List<Page> {
        val config = readUrlConfig(chapter.url)
        val source = sourceManager.getOrStub(config.source)
        return source.fetchPageList(
            SChapter.create().apply {
                copyFrom(chapter)
                url = config.url
            }
        ).map { pages ->
            pages.map { page ->
                page.copyWithUrl(writeUrlConfig(UrlConfig(config.source, page.url, config.mangaUrl)))
            }
        }.awaitSingle()
    }

    override suspend fun fetchImageUrlSuspended(page: Page): String {
        val config = readUrlConfig(page.url)
        val source = sourceManager.getOrStub(config.source) as? HttpSource ?: throw UnsupportedOperationException("This source does not support this operation!")
        return source.fetchImageUrl(page.copyWithUrl(config.url)).awaitSingle()
    }

    override suspend fun pageListParseSuspended(response: Response) = throw UnsupportedOperationException()
    override suspend fun imageUrlParseSuspended(response: Response) = throw UnsupportedOperationException()

    override fun fetchImage(page: Page): Observable<Response> {
        val config = readUrlConfig(page.url)
        val source = sourceManager.getOrStub(config.source) as? HttpSource
            ?: throw UnsupportedOperationException("This source does not support this operation!")
        return source.fetchImage(page.copyWithUrl(config.url))
    }

    override suspend fun prepareNewChapterSuspended(chapter: SChapter, manga: SManga) {
        val chapterConfig = readUrlConfig(chapter.url)
        val source = sourceManager.getOrStub(chapterConfig.source) as? HttpSource ?: throw UnsupportedOperationException("This source does not support this operation!")
        val copiedManga = SManga.create().apply {
            this.copyFrom(manga)
            url = chapterConfig.mangaUrl
        }
        chapter.url = chapterConfig.url
        source.prepareNewChapter(chapter, copiedManga)
        chapter.url = writeUrlConfig(UrlConfig(source.id, chapter.url, chapterConfig.mangaUrl))
        chapter.scanlator = if (chapter.scanlator.isNullOrBlank()) source.name
        else "$source: ${chapter.scanlator}"
    }

    fun readMangaConfig(manga: SManga): MangaConfig {
        return MangaConfig.readFromUrl(gson, manga.url)
    }

    fun readUrlConfig(url: String): UrlConfig {
        return gson.fromJson(url)
    }

    fun writeUrlConfig(urlConfig: UrlConfig): String {
        return gson.toJson(urlConfig)
    }

    data class LoadedMangaSource(val source: Source, val manga: Manga)
    data class MangaSource(
        @SerializedName("s")
        val source: Long,
        @SerializedName("u")
        val url: String
    ) {
        suspend fun load(db: DatabaseHelper, sourceManager: SourceManager): LoadedMangaSource? {
            val manga = db.getManga(url, source).await() ?: return null
            val source = sourceManager.getOrStub(source)
            return LoadedMangaSource(source, manga)
        }
    }

    data class MangaConfig(
        @SerializedName("c")
        val children: List<MangaSource>
    ) {
        fun load(db: DatabaseHelper, sourceManager: SourceManager): Flow<LoadedMangaSource> {
            return children.asFlow().map { mangaSource ->
                mangaSource.load(db, sourceManager) ?: run {
                    XLog.w("> Missing source manga: $mangaSource")
                    throw IllegalStateException("Missing source manga: $mangaSource")
                }
            }
        }

        fun writeAsUrl(gson: Gson): String {
            return gson.toJson(this)
        }

        companion object {
            fun readFromUrl(gson: Gson, url: String): MangaConfig {
                return gson.fromJson(url)
            }
        }
    }

    data class UrlConfig(
        @SerializedName("s")
        val source: Long,
        @SerializedName("u")
        val url: String,
        @SerializedName("m")
        val mangaUrl: String
    )

    fun Page.copyWithUrl(newUrl: String) = Page(
        index,
        newUrl,
        imageUrl,
        uri
    )

    override val lang = "all"
    override val supportsLatest = false
    override val name = "MergedSource"
}
