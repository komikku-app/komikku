package tachiyomi.domain.history.interactor

import exh.source.EH_SOURCE_ID
import exh.source.MERGED_SOURCE_ID
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga

@Execution(ExecutionMode.CONCURRENT)
class GetNextChaptersTest {

    private val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
    private val getMergedChaptersByMangaId = mockk<GetMergedChaptersByMangaId>()
    private val getManga = mockk<GetManga>()
    private val getChapter = mockk<GetChapter>()

    private val getNextChapters = GetNextChapters(
        getChaptersByMangaId = getChaptersByMangaId,
        getMergedChaptersByMangaId = getMergedChaptersByMangaId,
        getManga = getManga,
        historyRepository = mockk(),
        getChapter = getChapter,
    )

    private val numberSortManga = Manga.create().copy(id = MANGA_ID, chapterFlags = Manga.CHAPTER_SORTING_NUMBER)

    /**
     * Sets up [manga] with [chapters], where chapters from [excludedScanlator] are dropped when the filters apply.
     */
    private fun setUp(manga: Manga, chapters: List<Chapter>, excludedScanlator: String = EXCLUDED) {
        val filtered = chapters.filterNot { it.scanlator == excludedScanlator }
        coEvery { getManga.await(manga.id) } returns manga
        coEvery { getChaptersByMangaId.await(manga.id, applyFilter = true) } returns filtered
        coEvery { getMergedChaptersByMangaId.await(manga.id, any(), applyFilter = true) } returns filtered
        coEvery { getChapter.await(any<Long>()) } answers { chapters.find { it.id == firstArg<Long>() } }
    }

    private fun chapter(
        id: Long,
        number: Double,
        read: Boolean = false,
        scanlator: String = KEPT,
        sourceOrder: Long = 0,
    ) = Chapter.create().copy(
        id = id,
        mangaId = MANGA_ID,
        chapterNumber = number,
        read = read,
        scanlator = scanlator,
        sourceOrder = sourceOrder,
    )

    private fun List<Chapter>.ids() = map { it.id }

    // Chapter 3 exists from both scanlators; chapter 4 only from the excluded one
    private val chapters = listOf(
        chapter(1, 1.0, read = true),
        chapter(2, 2.0, read = true),
        chapter(3, 3.0),
        chapter(30, 3.0, scanlator = EXCLUDED),
        chapter(40, 4.0, scanlator = EXCLUDED),
        chapter(5, 5.0),
        chapter(6, 6.0, read = true),
        chapter(7, 7.0),
    )

    @Test
    fun `present unread chapter resumes from itself`() = runTest {
        setUp(numberSortManga, chapters)

        getNextChapters.await(MANGA_ID, 3, onlyUnread = false).ids() shouldBe listOf(3L, 5, 6, 7)
        getNextChapters.await(MANGA_ID, 3, onlyUnread = true).ids() shouldBe listOf(3L, 5, 7)
        coVerify(exactly = 0) { getChapter.await(any<Long>()) }
    }

    @Test
    fun `present read chapter resumes from the next chapter`() = runTest {
        setUp(numberSortManga, chapters)

        getNextChapters.await(MANGA_ID, 2, onlyUnread = false).ids() shouldBe listOf(3L, 5, 6, 7)
        coVerify(exactly = 0) { getChapter.await(any<Long>()) }
    }

    @Test
    fun `present read chapter with onlyUnread keeps returning every unread chapter`() = runTest {
        setUp(numberSortManga, chapters)

        // Unchanged behavior: the read chapter is missing from the unread list, so the list starts from the first one
        getNextChapters.await(MANGA_ID, 6, onlyUnread = true).ids() shouldBe listOf(3L, 5, 7)
    }

    @Test
    fun `excluded read chapter resumes from the next filtered chapter`() = runTest {
        val chapters = chapters.map { if (it.id == 30L || it.id == 40L) it.copy(read = true) else it }
        setUp(numberSortManga, chapters)

        getNextChapters.await(MANGA_ID, 40, onlyUnread = false).ids() shouldBe listOf(5L, 6, 7)
        getNextChapters.await(MANGA_ID, 40, onlyUnread = true).ids() shouldBe listOf(5L, 7)
        // Another scanlator's copy of a read chapter is skipped
        getNextChapters.await(MANGA_ID, 30, onlyUnread = false).ids() shouldBe listOf(5L, 6, 7)
        getNextChapters.await(MANGA_ID, 30, onlyUnread = true).ids() shouldBe listOf(5L, 7)
    }

    @Test
    fun `excluded unread chapter resumes from another scanlator's copy of it`() = runTest {
        setUp(numberSortManga, chapters)

        getNextChapters.await(MANGA_ID, 30, onlyUnread = false).ids() shouldBe listOf(3L, 5, 6, 7)
        getNextChapters.await(MANGA_ID, 30, onlyUnread = true).ids() shouldBe listOf(3L, 5, 7)
        getNextChapters.await(MANGA_ID, 40, onlyUnread = false).ids() shouldBe listOf(5L, 6, 7)
        getNextChapters.await(MANGA_ID, 40, onlyUnread = true).ids() shouldBe listOf(5L, 7)
    }

    @Test
    fun `excluded chapter follows source order sorting`() = runTest {
        // Source order is descending: the highest source order is the first chapter
        val manga = numberSortManga.copy(chapterFlags = Manga.CHAPTER_SORTING_SOURCE)
        val chapters = listOf(
            chapter(10, 0.0, read = true, sourceOrder = 5),
            chapter(1, 1.0, sourceOrder = 4),
            chapter(20, 2.0, read = true, scanlator = EXCLUDED, sourceOrder = 3),
            // Chapter numbers out of order to tell the sortings apart
            chapter(3, 9.0, sourceOrder = 2),
            chapter(4, 0.5, sourceOrder = 1),
        )
        setUp(manga, chapters)

        getNextChapters.await(MANGA_ID, 20, onlyUnread = false).ids() shouldBe listOf(3L, 4)
        getNextChapters.await(MANGA_ID, 20, onlyUnread = true).ids() shouldBe listOf(3L, 4)
    }

    @Test
    fun `excluded unread chapter resumes from another scanlator's copy that sorts before it`() = runTest {
        val manga = numberSortManga.copy(chapterFlags = Manga.CHAPTER_SORTING_SOURCE)
        val chapters = listOf(
            chapter(1, 1.0, read = true, sourceOrder = 3),
            chapter(2, 2.0, sourceOrder = 2),
            chapter(20, 2.0, scanlator = EXCLUDED, sourceOrder = 1),
            chapter(3, 3.0, sourceOrder = 0),
        )
        setUp(manga, chapters)

        getNextChapters.await(MANGA_ID, 20, onlyUnread = false).ids() shouldBe listOf(2L, 3)
        getNextChapters.await(MANGA_ID, 20, onlyUnread = true).ids() shouldBe listOf(2L, 3)
        // The copy of a read chapter is skipped
        setUp(manga, chapters.map { if (it.id == 20L) it.copy(read = true) else it })
        getNextChapters.await(MANGA_ID, 20, onlyUnread = false).ids() shouldBe listOf(3L)
    }

    @Test
    fun `excluded chapter with a tied upload date falls back to chapter number`() = runTest {
        // The source gives no upload dates, so every chapter ties
        val manga = numberSortManga.copy(chapterFlags = Manga.CHAPTER_SORTING_UPLOAD_DATE)
        val chapters = listOf(
            chapter(10, 0.0, read = true, sourceOrder = 5),
            chapter(1, 1.0, sourceOrder = 4),
            chapter(20, 2.0, scanlator = EXCLUDED, sourceOrder = 3),
            chapter(3, 3.0, sourceOrder = 2),
            chapter(4, 4.0, read = true, sourceOrder = 1),
        )
        setUp(manga, chapters)

        getNextChapters.await(MANGA_ID, 20, onlyUnread = false).ids() shouldBe listOf(3L, 4)
        getNextChapters.await(MANGA_ID, 20, onlyUnread = true).ids() shouldBe listOf(3L)
        setUp(manga, chapters.map { if (it.id == 20L) it.copy(read = true) else it })
        getNextChapters.await(MANGA_ID, 20, onlyUnread = false).ids() shouldBe listOf(3L, 4)
    }

    @Test
    fun `excluded chapter with a tied upload date and chapter number falls back to source order`() = runTest {
        val manga = numberSortManga.copy(chapterFlags = Manga.CHAPTER_SORTING_UPLOAD_DATE)
        val chapters = listOf(
            chapter(10, -1.0, read = true, sourceOrder = 5),
            chapter(11, -1.0, sourceOrder = 4),
            chapter(20, -1.0, scanlator = EXCLUDED, sourceOrder = 3),
            chapter(12, -1.0, sourceOrder = 2),
            chapter(13, -1.0, read = true, sourceOrder = 1),
        )
        setUp(manga, chapters)

        getNextChapters.await(MANGA_ID, 20, onlyUnread = false).ids() shouldBe listOf(12L, 13)
        getNextChapters.await(MANGA_ID, 20, onlyUnread = true).ids() shouldBe listOf(12L)
    }

    @Test
    fun `excluded chapter of an E-Hentai manga resumes from the next filtered chapter`() = runTest {
        val manga = numberSortManga.copy(source = EH_SOURCE_ID)
        val chapters = listOf(
            chapter(1, 1.0, read = true),
            chapter(2, 2.0, read = true),
            chapter(30, 3.0, read = true, scanlator = EXCLUDED),
            chapter(4, 4.0),
            chapter(5, 5.0),
            chapter(60, 6.0, read = true, scanlator = EXCLUDED),
        )
        setUp(manga, chapters)

        getNextChapters.await(MANGA_ID, 30, onlyUnread = false).ids() shouldBe listOf(4L, 5)
        // Only the last chapter is returned when only unread
        getNextChapters.await(MANGA_ID, 30, onlyUnread = true).ids() shouldBe listOf(5L)
        getNextChapters.await(MANGA_ID, 60, onlyUnread = false).ids() shouldBe emptyList()
        getNextChapters.await(MANGA_ID, 60, onlyUnread = true).ids() shouldBe emptyList()
    }

    @Test
    fun `excluded chapter of a merged manga with source order sorting compares chapter numbers`() = runTest {
        // Deduping gives the merged chapters a new source order, unrelated to the hidden chapter's own one
        val manga = numberSortManga.copy(source = MERGED_SOURCE_ID, chapterFlags = Manga.CHAPTER_SORTING_SOURCE)
        val chapters = listOf(
            chapter(1, 1.0, read = true, sourceOrder = 3),
            chapter(2, 2.0, sourceOrder = 2),
            chapter(30, 3.0, read = true, scanlator = EXCLUDED, sourceOrder = 100).copy(mangaId = OTHER_MANGA_ID),
            chapter(4, 4.0, sourceOrder = 1),
            chapter(5, 5.0, sourceOrder = 0),
        )
        setUp(manga, chapters)

        getNextChapters.await(MANGA_ID, 30, onlyUnread = false).ids() shouldBe listOf(4L, 5)
        getNextChapters.await(MANGA_ID, 30, onlyUnread = true).ids() shouldBe listOf(4L, 5)
    }

    @Test
    fun `failed filtered chapter query keeps the default behavior`() = runTest {
        setUp(numberSortManga, chapters)
        val filtered = chapters.filterNot { it.scanlator == EXCLUDED }
        coEvery { getChaptersByMangaId.await(MANGA_ID, applyFilter = true) } returnsMany listOf(filtered, emptyList())

        getNextChapters.await(MANGA_ID, 6, onlyUnread = true).ids() shouldBe listOf(3L, 5, 7)
    }

    @Test
    fun `excluded chapter of a merged manga resumes from the next filtered chapter`() = runTest {
        val manga = numberSortManga.copy(source = MERGED_SOURCE_ID)
        val chapters = chapters.map { if (it.id == 40L) it.copy(read = true, mangaId = OTHER_MANGA_ID) else it }
        setUp(manga, chapters)

        getNextChapters.await(MANGA_ID, 40, onlyUnread = false).ids() shouldBe listOf(5L, 6, 7)
        getNextChapters.await(MANGA_ID, 40, onlyUnread = true).ids() shouldBe listOf(5L, 7)
        coVerify(exactly = 0) { getChaptersByMangaId.await(any(), any()) }
    }

    @Test
    fun `missing chapter keeps the default behavior`() = runTest {
        setUp(numberSortManga, chapters)

        // Unchanged behavior: the list starts from the first chapter, which is dropped when not only unread
        getNextChapters.await(MANGA_ID, 999, onlyUnread = false).ids() shouldBe listOf(2L, 3, 5, 6, 7)
        getNextChapters.await(MANGA_ID, 999, onlyUnread = true).ids() shouldBe listOf(3L, 5, 7)
    }

    companion object {
        private const val MANGA_ID = 1L
        private const val OTHER_MANGA_ID = 2L
        private const val KEPT = "Kept"
        private const val EXCLUDED = "Excluded"
    }
}
