package tachiyomi.data.manga

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.chapter.ChapterMapper
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import java.util.Date
import kotlin.random.Random

// KMK -->
/** Verifies cached chapter aggregates against a Kotlin recomputation. */
class MangaChapterStatsTest {

    private lateinit var driver: SqlDriver
    private lateinit var db: Database
    private lateinit var chapterRepository: ChapterRepositoryImpl
    private lateinit var historyRepository: HistoryRepositoryImpl

    private val scanlators = listOf(null, "alpha", "beta", "gamma")

    @BeforeEach
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver).value
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        db = Database(
            driver = driver,
            historyAdapter = tachiyomi.data.History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
        )
        val handler = AndroidDatabaseHandler(db = db, driver = driver)
        chapterRepository = ChapterRepositoryImpl(handler)
        historyRepository = HistoryRepositoryImpl(handler)
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `aggregates survive a random sequence of writes`() {
        val rng = Random(20260725)
        seedLibrary(count = 12)

        repeat(400) {
            applyRandomWrite(rng)
            assertAggregatesMatch()
        }
    }

    @Test
    fun `deleting an entry leaves no orphaned stats`() {
        seedLibrary(count = 4)
        val ids = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsList().map { it.id }
        ids.forEach { insertChapter(it, scanlator = "alpha", read = true, bookmark = true) }

        ids.forEach { db.mangasQueries.deleteById(it) }

        statsRowCount() shouldBe 0
    }

    @Test
    fun `direct low-level deletion still recovers the maxima`() {
        // Bypasses the repository entirely, so only the trigger can keep this correct.
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        repeat(20) { i ->
            insertChapter(mangaId, scanlator = null, read = true, bookmark = false, dates = (i + 1).toLong())
        }
        val chapters = chaptersOf(mangaId)
        chapters.forEach { db.historyQueries.upsert(it.id, Date(it.dateUpload * 10), 1) }

        val holdingEveryMaximum = chapters.maxBy { it.dateUpload }
        db.chaptersQueries.removeChaptersWithIds(listOf(holdingEveryMaximum.id))

        libraryRow(mangaId).let {
            it.totalChapters shouldBe 19
            it.latestUpload shouldBe 19
            it.chapterFetchedAt shouldBe 19
            it.lastRead shouldBe 190
        }
        assertAggregatesMatch()
    }

    @Test
    fun `adversarial deletion through the repository path stays correct`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        // Dates fall as ids rise, and every chapter carries history: the worst shape.
        repeat(40) { i ->
            insertChapter(mangaId, scanlator = null, read = true, bookmark = true, dates = (40 - i).toLong())
        }
        val chapters = chaptersOf(mangaId)
        chapters.forEach { db.historyQueries.upsert(it.id, Date(it.dateUpload * 10), 1) }

        deleteChapters(chapters.sortedBy { it.dateUpload }.drop(5).map { it.id })

        libraryRow(mangaId).let {
            it.totalChapters shouldBe 5
            it.latestUpload shouldBe 5
            it.chapterFetchedAt shouldBe 5
            it.lastRead shouldBe 50
        }
        assertAggregatesMatch()
    }

    @Test
    fun `bulk deletion leaves the other entries untouched`() {
        seedLibrary(count = 4)
        val ids = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsList().map { it.id }
        ids.forEach { id ->
            repeat(5) { i ->
                insertChapter(id, scanlator = null, read = true, bookmark = false, dates = (i + 1).toLong())
            }
        }
        // Avoid the merge relationship created by seedLibrary.
        val deleteFrom = listOf(ids[0], ids[2])
        val untouched = ids[1]
        val before = libraryRow(untouched)

        deleteChapters(deleteFrom.flatMap { chaptersOf(it) }.map { it.id })

        libraryRow(untouched).let {
            it.totalChapters shouldBe before.totalChapters
            it.readCount shouldBe before.readCount
            it.latestUpload shouldBe before.latestUpload
        }
        deleteFrom.forEach { libraryRow(it).totalChapters shouldBe 0 }
        assertAggregatesMatch()
    }

    @Test
    fun `bulk history reset through the repository path stays correct`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        repeat(50) { i ->
            insertChapter(mangaId, scanlator = null, read = true, bookmark = false, dates = (i + 1).toLong())
        }
        // Read times fall as ids rise, so each reset would destroy the stored maximum.
        chaptersOf(mangaId).forEach { db.historyQueries.upsert(it.id, Date((60 - it.dateUpload) * 10), 1) }
        libraryRow(mangaId).lastRead shouldBe 590

        resetHistoryByMangaIds(listOf(mangaId))

        libraryRow(mangaId).lastRead shouldBe 0
        assertAggregatesMatch()
    }

    @Test
    fun `removing resetted history through the repository path stays correct`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        repeat(20) { i ->
            insertChapter(mangaId, scanlator = null, read = true, bookmark = false, dates = (i + 1).toLong())
        }
        val chapters = chaptersOf(mangaId).sortedBy { it.dateUpload }
        chapters.forEach { db.historyQueries.upsert(it.id, Date(it.dateUpload * 10), 1) }

        // Reset the newest half, leaving an all-equal zero set for the delete to walk.
        val newest = chapters.takeLast(10).map { it.id }.toSet()
        resetHistory(historyIdsFor(mangaId).filter { it.second in newest }.map { it.first })
        removeResettedHistory()

        // The newest ten are gone, so the oldest ten now hold the maximum.
        libraryRow(mangaId).lastRead shouldBe 100
        assertAggregatesMatch()
    }

    @Test
    fun `lowering the newest date recomputes it without a membership change`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        insertChapter(mangaId, scanlator = null, read = false, bookmark = false, dates = 100)
        insertChapter(mangaId, scanlator = null, read = false, bookmark = false, dates = 900)
        val newest = chaptersOf(mangaId).maxBy { it.dateUpload }

        libraryRow(mangaId).latestUpload shouldBe 900

        // Pull the newest chapter below the runner-up; the stored maximum must follow it down.
        db.chaptersQueries.update(
            mangaId = null, url = null, name = null, scanlator = null,
            read = null, bookmark = null, lastPageRead = null, chapterNumber = null,
            sourceOrder = null, dateFetch = 50, dateUpload = 50,
            chapterId = newest.id, version = null, isSyncing = 0, memo = null,
        )

        libraryRow(mangaId).let {
            it.latestUpload shouldBe 100
            it.chapterFetchedAt shouldBe 100
        }
        assertAggregatesMatch()
    }

    @Test
    fun `raising a date and flipping read in one update keeps both correct`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        insertChapter(mangaId, scanlator = null, read = false, bookmark = false, dates = 100)
        val chapter = chaptersOf(mangaId).single()

        db.chaptersQueries.update(
            mangaId = null, url = null, name = null, scanlator = null,
            read = true, bookmark = true, lastPageRead = null, chapterNumber = null,
            sourceOrder = null, dateFetch = 700, dateUpload = 700,
            chapterId = chapter.id, version = null, isSyncing = 0, memo = null,
        )

        libraryRow(mangaId).let {
            it.readCount shouldBe 1
            it.bookmarkCount shouldBe 1
            it.bookmarkReadCount shouldBe 1
            it.latestUpload shouldBe 700
        }
        assertAggregatesMatch()
    }

    @Test
    fun `excluding a scanlator removes its chapters from the counts`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        insertChapter(mangaId, scanlator = "alpha", read = true, bookmark = false)
        insertChapter(mangaId, scanlator = "beta", read = true, bookmark = false)

        libraryRow(mangaId).totalChapters shouldBe 2

        db.excluded_scanlatorsQueries.insert(mangaId, "beta")
        libraryRow(mangaId).totalChapters shouldBe 1
        assertAggregatesMatch()

        db.excluded_scanlatorsQueries.remove(mangaId, listOf("beta"))
        libraryRow(mangaId).totalChapters shouldBe 2
        assertAggregatesMatch()
    }

    // ------------------------------------------------------------------ oracle

    private fun assertAggregatesMatch() {
        db.libraryViewQueries.library(MangaMapper::mapLibraryManga).executeAsList().forEach { row ->
            val expected = recompute(sourceMangaIdsFor(row.id))
            withClue(row.id) {
                row.totalChapters shouldBe expected.total
                row.readCount shouldBe expected.read
                row.bookmarkCount shouldBe expected.bookmark
                row.bookmarkReadCount shouldBe expected.bookmarkRead
                row.latestUpload shouldBe expected.latestUpload
                row.chapterFetchedAt shouldBe expected.fetchedAt
                row.lastRead shouldBe expected.lastRead
            }
        }
    }

    /** A merged entry aggregates child chapters. */
    private fun sourceMangaIdsFor(mangaId: Long): List<Long> {
        val children = db.mergedQueries.selectByMergeId(mangaId).executeAsList().mapNotNull { it.manga_id }
        return children.ifEmpty { listOf(mangaId) }
    }

    private data class Aggregate(
        val total: Long = 0,
        val read: Long = 0,
        val bookmark: Long = 0,
        val bookmarkRead: Long = 0,
        val latestUpload: Long = 0,
        val fetchedAt: Long = 0,
        val lastRead: Long = 0,
    )

    private fun recompute(mangaIds: List<Long>): Aggregate {
        var agg = Aggregate()
        mangaIds.forEach { mangaId ->
            val excluded = db.excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
                .executeAsList()
                .filterNotNull()
                .toSet()
            val history = db.historyQueries.getHistoryByMangaId(mangaId) { _, chapterId, lastRead, _ ->
                chapterId to (lastRead?.time ?: 0L)
            }.executeAsList().toMap()

            db.chaptersQueries
                .getChaptersByMangaId(mangaId, 0L, 0L, 0L, ChapterMapper::mapChapter)
                .executeAsList()
                .filter { it.scanlator !in excluded }
                .forEach { chapter ->
                    agg = agg.copy(
                        total = agg.total + 1,
                        read = agg.read + if (chapter.read) 1 else 0,
                        bookmark = agg.bookmark + if (chapter.bookmark) 1 else 0,
                        bookmarkRead = agg.bookmarkRead + if (chapter.bookmark && chapter.read) 1 else 0,
                        latestUpload = maxOf(agg.latestUpload, chapter.dateUpload),
                        fetchedAt = maxOf(agg.fetchedAt, chapter.dateFetch),
                        lastRead = maxOf(agg.lastRead, history[chapter.id] ?: 0L),
                    )
                }
        }
        return agg
    }

    // ------------------------------------------------------------------ writes

    private fun applyRandomWrite(rng: Random) {
        val mangaIds = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsList().map { it.id }
        val chapters = mangaIds.flatMap { chaptersOf(it) }

        when (rng.nextInt(10)) {
            0, 1, 2 -> insertChapter(
                mangaIds.random(rng),
                scanlators.random(rng),
                rng.nextBoolean(),
                rng.nextBoolean(),
                rng,
            )
            3, 4 -> chapters.randomOrNull(rng)?.let { chapter ->
                // Match ChapterRepositoryImpl.partialUpdate.
                db.chaptersQueries.update(
                    mangaId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = rng.nextBoolean(),
                    bookmark = rng.nextBoolean(),
                    lastPageRead = rng.nextLong(50),
                    chapterNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    chapterId = chapter.id,
                    version = null,
                    isSyncing = 0,
                    memo = null,
                )
            }
            5 -> chapters.randomOrNull(rng)?.let { chapter ->
                // Simulate the reader page update.
                db.chaptersQueries.update(
                    mangaId = null, url = null, name = null, scanlator = null,
                    read = null, bookmark = null, lastPageRead = rng.nextLong(50),
                    chapterNumber = null, sourceOrder = null, dateFetch = null, dateUpload = null,
                    chapterId = chapter.id, version = null, isSyncing = 0, memo = null,
                )
            }
            6 -> chapters.randomOrNull(rng)?.let { chapter ->
                db.chaptersQueries.update(
                    mangaId = null, url = null, name = null, scanlator = scanlators.random(rng),
                    read = null, bookmark = null, lastPageRead = null, chapterNumber = null,
                    sourceOrder = null, dateFetch = rng.nextLong(1, 5_000),
                    dateUpload = rng.nextLong(1, 5_000),
                    chapterId = chapter.id, version = null, isSyncing = 0, memo = null,
                )
            }
            7 -> chapters.randomOrNull(rng)?.let { chapter ->
                db.historyQueries.upsert(chapter.id, Date(rng.nextLong(1, 9_000)), rng.nextLong(1, 60))
            }
            8 -> when (rng.nextInt(3)) {
                0 -> db.historyQueries.resetHistoryByMangaIds(listOf(mangaIds.random(rng)))
                1 -> db.historyQueries.removeAllHistory()
                else -> chapters.randomOrNull(rng)
                    ?.let { deleteChapters(listOf(it.id)) }
            }
            else -> {
                val mangaId = mangaIds.random(rng)
                val scanlator = scanlators.filterNotNull().random(rng)
                val current = db.excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
                    .executeAsList()
                if (scanlator in current) {
                    db.excluded_scanlatorsQueries.remove(mangaId, listOf(scanlator))
                } else {
                    db.excluded_scanlatorsQueries.insert(mangaId, scanlator)
                }
            }
        }
    }

    private fun seedLibrary(count: Int) {
        repeat(count) { i ->
            db.mangasQueries.insert(
                // The final entry is the merge parent.
                source = if (count >= 3 && i == count - 1) MERGED_SOURCE_ID else 1L,
                url = "/manga/$i",
                artist = null,
                author = null,
                description = null,
                genre = null,
                title = "Manga $i",
                status = 0,
                thumbnailUrl = null,
                favorite = true,
                lastUpdate = 0,
                nextUpdate = 0,
                initialized = true,
                viewerFlags = 0,
                chapterFlags = 0,
                coverLastModified = 0,
                dateAdded = 0,
                updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
                calculateInterval = 0,
                version = 0,
                notes = "",
                memo = JsonObject(emptyMap()),
            )
        }
        if (count >= 3) {
            val ids = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsList().map { it.id }
            val mergeId = ids.last()
            // Add two merge children.
            listOf(ids[0], ids[1]).forEach { childId ->
                db.mergedQueries.insert(
                    infoManga = true,
                    getChapterUpdates = true,
                    chapterSortMode = 0,
                    chapterPriority = 0,
                    downloadChapters = true,
                    mergeId = mergeId,
                    mergeUrl = "/merge",
                    mangaId = childId,
                    mangaUrl = "/manga/$childId",
                    mangaSource = 1,
                )
            }
        }
    }

    private fun insertChapter(
        mangaId: Long,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        rng: Random = Random(0),
        dates: Long? = null,
    ) {
        db.chaptersQueries.insert(
            mangaId = mangaId,
            url = "/chapter/${rng.nextLong()}",
            name = "Chapter",
            scanlator = scanlator,
            read = read,
            bookmark = bookmark,
            lastPageRead = 0,
            chapterNumber = 1.0,
            sourceOrder = 0,
            dateFetch = dates ?: rng.nextLong(1, 5_000),
            dateUpload = dates ?: rng.nextLong(1, 5_000),
            version = 0,
            memo = JsonObject(emptyMap()),
        )
    }

    private fun deleteChapters(chapterIds: List<Long>) = runBlocking {
        chapterRepository.removeChaptersWithIds(chapterIds)
    }

    private fun historyIdsFor(mangaId: Long): List<Pair<Long, Long>> = db.historyQueries
        .getHistoryByMangaId(mangaId) { id, chapterId, _, _ -> id to chapterId }
        .executeAsList()

    private fun resetHistory(historyIds: List<Long>) = runBlocking {
        historyRepository.resetHistory(historyIds)
    }

    private fun resetHistoryByMangaIds(mangaIds: List<Long>) = runBlocking {
        historyRepository.resetHistoryByMangaIds(mangaIds)
    }

    private fun removeResettedHistory() = runBlocking {
        historyRepository.removeResettedHistory()
    }

    private fun chaptersOf(mangaId: Long) = db.chaptersQueries
        .getChaptersByMangaId(mangaId, 0L, 0L, 0L, ChapterMapper::mapChapter)
        .executeAsList()

    private fun libraryRow(mangaId: Long) = db.libraryViewQueries
        .library(MangaMapper::mapLibraryManga)
        .executeAsList()
        .single { it.id == mangaId }

    private fun statsRowCount(): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT count(*) FROM manga_chapter_stats",
        parameters = 0,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0)!!)
        },
    ).value

    private fun <T> withClue(clue: Any, block: () -> T): T = try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("manga $clue: ${e.message}", e)
    }

    companion object {
        private const val MERGED_SOURCE_ID = 6969L

        // Manga resolves custom entry info through Injekt as soon as one is built.
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfo() {
            Injekt.addSingletonFactory {
                GetCustomMangaInfo(
                    object : CustomMangaRepository {
                        override fun get(mangaId: Long) = null
                        override fun set(mangaInfo: CustomMangaInfo) = Unit
                    },
                )
            }
        }
    }
}
// KMK <--
