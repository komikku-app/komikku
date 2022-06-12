package eu.kanade.tachiyomi.source.online.all

import eu.kanade.domain.chapter.interactor.GetMergedChapterByMangaId
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.manga.model.toDbManga
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import exh.log.xLogW
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
import eu.kanade.domain.chapter.model.Chapter as DomainChapter
import eu.kanade.domain.manga.model.Manga as DomainManga

class MergedSource : HttpSource() {
    private val db: DatabaseHelper by injectLazy()
    private val getMergedChaptersByMangaId: GetMergedChapterByMangaId by injectLazy()
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
            val mergedManga = db.getManga(manga.key, id).executeAsBlocking()
                ?: throw Exception("merged manga not in db")
            val mangaReferences = db.getMergedMangaReferences(mergedManga.id!!).executeAsBlocking()
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
                db.getManga(mangaUrl, mangaSourceId).executeAsBlocking()?.toMangaInfo()
            }
            (dbManga ?: mergedManga.toMangaInfo()).copy(
                key = manga.key,
            )
        }
    }

    // TODO more chapter dedupe
    fun transformMergedChapters(mangaId: Long, chapterList: List<DomainChapter>, editScanlators: Boolean, dedupe: Boolean): List<DomainChapter> {
        val mangaReferences = db.getMergedMangaReferences(mangaId).executeAsBlocking()
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
        return transformMergedChapters(mangaId, runBlocking { getMergedChaptersByMangaId.await(mangaId) }, editScanlators, dedupe)
            .map(DomainChapter::toDbChapter)
    }

    private fun dedupeChapterList(mangaReferences: List<MergedMangaReference>, chapterList: List<DomainChapter>): List<DomainChapter> {
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

    private fun findSourceWithMostChapters(chapterList: List<DomainChapter>): Long? {
        return chapterList.groupBy { it.mangaId }.maxByOrNull { it.value.size }?.key
    }

    private fun findSourceWithHighestChapterNumber(chapterList: List<DomainChapter>): Long? {
        return chapterList.maxByOrNull { it.chapterNumber }?.mangaId
    }

    suspend fun fetchChaptersForMergedManga(manga: DomainManga, downloadChapters: Boolean = true, editScanlators: Boolean = false, dedupe: Boolean = true): List<Chapter> {
        return withIOContext {
            fetchChaptersAndSync(manga, downloadChapters)
            getChaptersAsBlocking(manga.id, editScanlators, dedupe)
        }
    }

    suspend fun fetchChaptersAndSync(manga: DomainManga, downloadChapters: Boolean = true): Pair<List<DomainChapter>, List<DomainChapter>> {
        val syncChaptersWithSource = Injekt.get<SyncChaptersWithSource>()
        val mangaReferences = db.getMergedMangaReferences(manga.id).executeAsBlocking()
        if (mangaReferences.isEmpty()) {
            throw IllegalArgumentException("Manga references are empty, chapters unavailable, merge is likely corrupted")
        }

        val ifDownloadNewChapters = downloadChapters && manga.toDbManga().shouldDownloadNewChapters(db, preferences)
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
                                        it.load(db, sourceManager)
                                    if (loadedManga != null && reference.getChapterUpdates) {
                                        val chapterList = source.getChapterList(loadedManga.toMangaInfo())
                                            .map(ChapterInfo::toSChapter)
                                        val results =
                                            syncChaptersWithSource.await(chapterList, loadedManga.toDomainManga()!!, source)
                                        if (ifDownloadNewChapters && reference.downloadChapters) {
                                            downloadManager.downloadChapters(
                                                loadedManga,
                                                results.first.map(DomainChapter::toDbChapter),
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

    suspend fun MergedMangaReference.load(db: DatabaseHelper, sourceManager: SourceManager): LoadedMangaSource {
        var manga = db.getManga(mangaUrl, mangaSourceId).executeAsBlocking()
        val source = sourceManager.getOrStub(manga?.source ?: mangaSourceId)
        if (manga == null) {
            manga = Manga.create(mangaSourceId).apply {
                url = mangaUrl
            }
            manga.copyFrom(source.getMangaDetails(manga.toMangaInfo()).toSManga())
            try {
                manga.id = db.insertManga(manga).executeAsBlocking().insertedId()
                mangaId = manga.id
                db.insertNewMergedMangaId(this).executeAsBlocking()
            } catch (e: Exception) {
                xLogW("Error inserting merged manga id", e)
            }
        }
        return LoadedMangaSource(source, manga, this)
    }

    data class LoadedMangaSource(val source: Source, val manga: Manga?, val reference: MergedMangaReference)

    override val lang = "all"
    override val supportsLatest = false
    override val name = "MergedSource"
}
