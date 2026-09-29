package eu.kanade.tachiyomi.source.online.all

import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.copy
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import mihon.domain.source.interactor.UpdateMangaFromRemote
import okhttp3.Response
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy

class MergedSource : HttpSource() {
    private val getManga: GetManga by injectLazy()
    private val getMergedReferencesById: GetMergedReferencesById by injectLazy()
    private val networkToLocalManga: NetworkToLocalManga by injectLazy()
    private val updateMangaFromRemote: UpdateMangaFromRemote by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val downloadManager: DownloadManager by injectLazy()
    private val filterChaptersForDownload: FilterChaptersForDownload by injectLazy()

    override val id: Long = MERGED_SOURCE_ID

    override val baseUrl = ""

    @Deprecated("Use the combined suspend API instead", replaceWith = ReplaceWith("getMangaUpdate"))
    override fun fetchChapterList(manga: SManga) = throw UnsupportedOperationException()
    override suspend fun getImage(page: Page): Response = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getImageUrl"))
    override fun fetchImageUrl(page: Page) = throw UnsupportedOperationException()
    override suspend fun getImageUrl(page: Page) = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getPageList"))
    override fun fetchPageList(chapter: SChapter) = throw UnsupportedOperationException()
    override suspend fun getPageList(chapter: SChapter) = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int) = throw UnsupportedOperationException()
    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", replaceWith = ReplaceWith("getPopularManga"))
    override fun fetchPopularManga(page: Int) = throw UnsupportedOperationException()
    override suspend fun getPopularManga(page: Int) = throw UnsupportedOperationException()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // KMK -->
        val asyncManga = if (fetchDetails) getMangaDetails(manga) else null
        return SMangaUpdate(asyncManga ?: manga, chapters)
        // KMK <--
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        return withIOContext {
            val mergedManga = requireNotNull(getManga.await(manga.url, id)) { "merged manga not in db" }
            val mangaReferences = getMergedReferencesById.await(mergedManga.id)
                .apply {
                    require(isNotEmpty()) { "Manga references are empty, info unavailable, merge is likely corrupted" }
                    require(!(size == 1 && first().mangaSourceId == MERGED_SOURCE_ID)) {
                        "Manga references contain only the merged reference, merge is likely corrupted"
                    }
                }

            val mangaInfoReference = mangaReferences.firstOrNull { it.isInfoManga }
                ?: mangaReferences.firstOrNull { it.mangaId != it.mergeId }
            val dbManga = mangaInfoReference?.run {
                getManga.await(mangaUrl, mangaSourceId)?.toSManga()
            }
            (dbManga ?: mergedManga.toSManga()).copy(
                url = manga.url,
            )
        }
    }

    suspend fun fetchChaptersAndSync(manga: Manga, downloadChapters: Boolean = true): List<Chapter> {
        val mangaReferences = getMergedReferencesById.await(manga.id)
        require(mangaReferences.isNotEmpty()) {
            "Manga references are empty, chapters unavailable, merge is likely corrupted"
        }

        val semaphore = Semaphore(5)
        var exception: Exception? = null
        return supervisorScope {
            mangaReferences
                .groupBy(MergedMangaReference::mangaSourceId)
                .minus(MERGED_SOURCE_ID)
                .map { (_, values) ->
                    async {
                        semaphore.withPermit {
                            values.flatMap {
                                try {
                                    val (source, loadedManga, reference) = it.load()
                                    if (loadedManga != null && reference.getChapterUpdates) {
                                        val results = updateMangaFromRemote(
                                            source = source,
                                            manga = loadedManga,
                                            fetchChapters = true,
                                        ).getOrThrow().newChapters

                                        if (downloadChapters && reference.downloadChapters) {
                                            val chaptersToDownload = filterChaptersForDownload.await(manga, results)
                                            if (chaptersToDownload.isNotEmpty()) {
                                                downloadManager.downloadChapters(
                                                    loadedManga,
                                                    chaptersToDownload,
                                                )
                                            }
                                        }
                                        results
                                    } else {
                                        emptyList()
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    exception = e
                                    emptyList()
                                }
                            }
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }.also {
            exception?.let { throw it }
        }
    }

    suspend fun MergedMangaReference.load(): LoadedMangaSource {
        var manga = getManga.await(mangaUrl, mangaSourceId)
        val source = sourceManager.getOrStub(manga?.source ?: mangaSourceId)
        if (manga == null) {
            val newManga = networkToLocalManga(
                Manga.create().copy(
                    source = mangaSourceId,
                    url = mangaUrl,
                ),
            )
            manga = updateMangaFromRemote(
                source = source,
                manga = newManga,
                fetchDetails = true,
            ).getOrThrow().manga
        }
        return LoadedMangaSource(source, manga, this)
    }

    data class LoadedMangaSource(val source: Source, val manga: Manga?, val reference: MergedMangaReference)

    override val lang = "all"
    override val supportsLatest = false
    override val name = "MergedSource"
}
