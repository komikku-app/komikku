package eu.kanade.tachiyomi.source.online.all

import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.chapter.interactor.GetMergedChapterByMangaId
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.GetMergedReferencesById
import eu.kanade.domain.manga.interactor.InsertManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.toMangaInfo
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import exh.merged.sql.models.MergedMangaReference
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Response
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MergedSource : HttpSource() {
    private val getManga: GetManga by injectLazy()
    private val getMergedReferencesById: GetMergedReferencesById by injectLazy()
    private val getMergedChaptersByMangaId: GetMergedChapterByMangaId by injectLazy()
    private val insertManga: InsertManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val downloadManager: DownloadManager by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    override val id: Long = MERGED_SOURCE_ID

    override val baseUrl = ""

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
    override fun pageListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
    override fun fetchChapterList(manga: SManga) = throw UnsupportedOperationException()
    override suspend fun getChapterList(manga: MangaInfo) = throw UnsupportedOperationException()
    override fun fetchImage(page: Page) = throw UnsupportedOperationException()
    override fun fetchImageUrl(page: Page) = throw UnsupportedOperationException()
    override fun fetchPageList(chapter: SChapter) = throw UnsupportedOperationException()
    override suspend fun getPageList(chapter: ChapterInfo) = throw UnsupportedOperationException()
    override fun fetchLatestUpdates(page: Int) = throw UnsupportedOperationException()
    override fun fetchPopularManga(page: Int) = throw UnsupportedOperationException()

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        return withIOContext {
            val mergedManga = getManga.await(manga.key, id) ?: throw Exception("merged manga not in db")
            val mangaReferences = getMergedReferencesById.await(mergedManga.id)
                .apply {
                    if (isEmpty()) {
                        throw IllegalArgumentException(
                            "Manga references are empty, info unavailable, merge is likely corrupted",
                        )
                    }
                    if (size == 1 && first().mangaSourceId == MERGED_SOURCE_ID) {
                        throw IllegalArgumentException(
                            "Manga references contain only the merged reference, merge is likely corrupted",
                        )
                    }
                }

            val mangaInfoReference = mangaReferences.firstOrNull { it.isInfoManga }
                ?: mangaReferences.firstOrNull { it.mangaId != it.mergeId }
            val dbManga = mangaInfoReference?.run {
                getManga.await(mangaUrl, mangaSourceId)?.toMangaInfo()
            }
            (dbManga ?: mergedManga.toMangaInfo()).copy(
                key = manga.key,
            )
        }
    }

    // TODO more chapter dedupe
    suspend fun transformMergedChapters(mangaId: Long, chapterList: List<Chapter>, editScanlators: Boolean, dedupe: Boolean): List<Chapter> {
        val mangaReferences = getMergedReferencesById.await(mangaId)
        val chapters = if (editScanlators) {
            val sources = mangaReferences.map { sourceManager.getOrStub(it.mangaSourceId) to it.mangaId }
            chapterList.map { chapter ->
                val source = sources.firstOrNull { chapter.mangaId == it.second }?.first
                if (source != null) {
                    chapter.copy(
                        scanlator = if (chapter.scanlator.isNullOrBlank()) {
                            source.name
                        } else {
                            "$source: ${chapter.scanlator}"
                        },
                    )
                } else chapter
            }
        } else chapterList
        return if (dedupe) dedupeChapterList(mangaReferences, chapters) else chapters
    }

    fun getChaptersAsBlocking(mangaId: Long, editScanlators: Boolean = false, dedupe: Boolean = true): List<Chapter> {
        return runBlocking { getChapters(mangaId, editScanlators, dedupe) }
    }

    suspend fun getChapters(mangaId: Long, editScanlators: Boolean = false, dedupe: Boolean = true): List<Chapter> {
        return transformMergedChapters(mangaId, getMergedChaptersByMangaId.await(mangaId), editScanlators, dedupe)
    }

    private fun dedupeChapterList(mangaReferences: List<MergedMangaReference>, chapterList: List<Chapter>): List<Chapter> {
        return when (mangaReferences.firstOrNull { it.mangaSourceId == MERGED_SOURCE_ID }?.chapterSortMode) {
            MergedMangaReference.CHAPTER_SORT_NO_DEDUPE, MergedMangaReference.CHAPTER_SORT_NONE -> chapterList
            MergedMangaReference.CHAPTER_SORT_PRIORITY -> chapterList
            MergedMangaReference.CHAPTER_SORT_MOST_CHAPTERS -> {
                findSourceWithMostChapters(chapterList)?.let { mangaId ->
                    chapterList.filter { it.mangaId == mangaId }
                } ?: chapterList
            }
            MergedMangaReference.CHAPTER_SORT_HIGHEST_CHAPTER_NUMBER -> {
                findSourceWithHighestChapterNumber(chapterList)?.let { mangaId ->
                    chapterList.filter { it.mangaId == mangaId }
                } ?: chapterList
            }
            else -> chapterList
        }
    }

    private fun findSourceWithMostChapters(chapterList: List<Chapter>): Long? {
        return chapterList.groupBy { it.mangaId }.maxByOrNull { it.value.size }?.key
    }

    private fun findSourceWithHighestChapterNumber(chapterList: List<Chapter>): Long? {
        return chapterList.maxByOrNull { it.chapterNumber }?.mangaId
    }

    suspend fun fetchChaptersForMergedManga(manga: Manga, downloadChapters: Boolean = true, editScanlators: Boolean = false, dedupe: Boolean = true): List<Chapter> {
        return withIOContext {
            fetchChaptersAndSync(manga, downloadChapters)
            getChapters(manga.id, editScanlators, dedupe)
        }
    }

    suspend fun fetchChaptersAndSync(manga: Manga, downloadChapters: Boolean = true): Pair<List<Chapter>, List<Chapter>> {
        val syncChaptersWithSource = Injekt.get<SyncChaptersWithSource>()
        val mangaReferences = getMergedReferencesById.await(manga.id)
        if (mangaReferences.isEmpty()) {
            throw IllegalArgumentException("Manga references are empty, chapters unavailable, merge is likely corrupted")
        }

        val ifDownloadNewChapters = downloadChapters && manga.shouldDownloadNewChapters(getCategories.await(manga.id).map { it.id }, preferences)
        val semaphore = Semaphore(5)
        var exception: Exception? = null
        return supervisorScope {
            mangaReferences
                .groupBy(MergedMangaReference::mangaSourceId)
                .minus(MERGED_SOURCE_ID)
                .map { (_, values) ->
                    async {
                        semaphore.withPermit {
                            values.map {
                                try {
                                    val (source, loadedManga, reference) =
                                        it.load(sourceManager, getManga, insertManga, updateManga)
                                    if (loadedManga != null && reference.getChapterUpdates) {
                                        val chapterList = source.getChapterList(loadedManga.toMangaInfo())
                                            .map(ChapterInfo::toSChapter)
                                        val results =
                                            syncChaptersWithSource.await(chapterList, loadedManga, source)
                                        if (ifDownloadNewChapters && reference.downloadChapters) {
                                            downloadManager.downloadChapters(
                                                loadedManga,
                                                results.first.map(Chapter::toDbChapter),
                                            )
                                        }
                                        results
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    exception = e
                                    null
                                }
                            }
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .let { pairs ->
                    pairs.flatMap { it?.first.orEmpty() } to pairs.flatMap { it?.second.orEmpty() }
                }
        }.also {
            exception?.let { throw it }
        }
    }

    suspend fun MergedMangaReference.load(sourceManager: SourceManager, getManga: GetManga, insertManga: InsertManga, updateManga: UpdateManga): LoadedMangaSource {
        var manga = getManga.await(mangaUrl, mangaSourceId)
        val source = sourceManager.getOrStub(manga?.source ?: mangaSourceId)
        if (manga == null) {
            val id = insertManga.await(
                Manga.create().copy(
                    source = mangaSourceId,
                    url = mangaUrl,
                ),
            )!!
            val newManga = getManga.await(id)!!
            updateManga.awaitUpdateFromSource(newManga, source.getMangaDetails(newManga.toMangaInfo()), false)
            manga = getManga.await(id)!!
        }
        return LoadedMangaSource(source, manga, this)
    }

    data class LoadedMangaSource(val source: Source, val manga: Manga?, val reference: MergedMangaReference)

    override val lang = "all"
    override val supportsLatest = false
    override val name = "MergedSource"
}
