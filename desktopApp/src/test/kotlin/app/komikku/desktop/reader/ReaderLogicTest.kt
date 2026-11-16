package app.komikku.desktop.reader

import app.komikku.desktop.di.DesktopInjekt
import app.komikku.shared.chapter.ChapterListActions
import app.komikku.shared.reader.ReaderChapterList
import app.komikku.shared.reader.ReaderProgressChapter
import app.komikku.shared.reader.ReaderProgressSaver
import app.komikku.shared.reader.ReaderTrackerHook
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * 7.6 / 6L.1: the shared reader chapter list (order, skip read / filtered / duplicates, neighbours),
 * "mark previous as read", and [ReaderProgressSaver] against the real database: last page, read at the
 * last page, duplicate chapters, incognito, bookmarks, history duration, tracker hook.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderLogicTest {

    @BeforeAll
    fun boot(@TempDir dir: File) {
        DesktopInjekt.init(dir)
    }

    private fun chapter(id: Long, number: Double, sourceOrder: Long, read: Boolean = false, scanlator: String? = null, bookmark: Boolean = false) =
        Chapter.create().copy(
            id = id,
            mangaId = 1,
            chapterNumber = number,
            sourceOrder = sourceOrder,
            read = read,
            scanlator = scanlator,
            bookmark = bookmark,
            name = "Chapter $number",
        )

    // Source order 0 = newest, as sources return them
    private val chapters = listOf(
        chapter(4, 4.0, 0),
        chapter(3, 3.0, 1, read = true),
        chapter(2, 2.0, 2, scanlator = "A"),
        chapter(5, 2.0, 3, scanlator = "B"),
        chapter(1, 1.0, 4, read = true),
    )

    private val manga = Manga.create().copy(id = 1, chapterFlags = Manga.CHAPTER_SORTING_SOURCE or Manga.CHAPTER_SORT_DESC)

    @Test
    fun `reader order is ascending in the manga's sort mode`() {
        val list = ReaderChapterList.build(manga, chapters, selectedChapterId = 2, ReaderChapterList.Options(skipFiltered = false))
        list.map { it.id } shouldBe listOf(1L, 5, 2, 3, 4)
        ReaderChapterList.neighbours(list, 2).let { (prev, next) -> prev?.id to next?.id } shouldBe (5L to 3L)
        ReaderChapterList.neighbours(list, 4).second shouldBe null

        val byNumber = manga.copy(chapterFlags = Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_DESC)
        ReaderChapterList.build(byNumber, chapters, 2, ReaderChapterList.Options(skipFiltered = false))
            .map { it.chapterNumber } shouldBe listOf(1.0, 2.0, 2.0, 3.0, 4.0)
    }

    @Test
    fun `skip read, filtered and duplicates keep the selected chapter`() {
        ReaderChapterList.build(manga, chapters, selectedChapterId = 3, ReaderChapterList.Options(skipRead = true))
            .map { it.id } shouldBe listOf(5L, 2, 3, 4)
        ReaderChapterList.build(manga, chapters, selectedChapterId = 2, ReaderChapterList.Options(skipDupe = true, skipFiltered = false))
            .map { it.id } shouldBe listOf(1L, 2, 3, 4)
        val unreadOnly = manga.copy(chapterFlags = manga.chapterFlags or Manga.CHAPTER_SHOW_UNREAD)
        ReaderChapterList.build(unreadOnly, chapters, selectedChapterId = 1, ReaderChapterList.Options(skipFiltered = true))
            .map { it.id } shouldBe listOf(1L, 5, 2, 4)
    }

    @Test
    fun `mark previous as read follows the displayed order`() {
        val displayed = ChapterListActions.sortForDisplay(manga, chapters) // descending: 4,3,5? by source order
        displayed.map { it.id } shouldBe listOf(4L, 3, 2, 5, 1)
        ChapterListActions.previousChapters(manga, displayed, chapters.first { it.id == 2L }).map { it.id } shouldBe listOf(1L, 5)
    }

    @Test
    fun `progress, completion, duplicates, incognito, bookmark and history are stored`() = runBlocking {
        val repo = Injekt.get<ChapterRepository>()
        val prefs = Injekt.get<LibraryPreferences>()
        prefs.markDuplicateReadChapterAsRead().set(setOf(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING))
        val stored = TestLibrary.insertManga(1234, "/m/progress", "Progress manga")
        val (c2, c2dup, c1) = TestLibrary.insertChapters(stored, "/c/2" to "Chapter 2", "/c/2b" to "Chapter 2 (B)", "/c/1" to "Chapter 1")
        // insertChapters numbers them 3, 2, 1: make the second one a duplicate of the first
        repo.updateAll(listOf(tachiyomi.domain.chapter.model.ChapterUpdate(id = c2dup.id, chapterNumber = c2.chapterNumber)))

        val tracked = mutableListOf<Double>()
        var now = 1_000_000L
        val saver = ReaderProgressSaver(
            updateChapter = Injekt.get(),
            upsertHistory = Injekt.get(),
            getChaptersByMangaId = Injekt.get(),
            libraryPreferences = prefs,
            trackerHook = ReaderTrackerHook { _, number -> tracked += number },
            clock = { now },
        )
        val progress = ReaderProgressChapter(c2.id, stored.id, c2.sourceOrder, c2.chapterNumber, read = false, lastPageRead = 0, bookmark = false)

        // Incognito: nothing is written
        saver.onPageSelected(stored, progress, pageIndex = 1, pageCount = 3, incognito = true) shouldBe false
        repo.getChapterById(c2.id)!!.lastPageRead shouldBe 0

        saver.restartReadTimer()
        saver.onPageSelected(stored, progress, pageIndex = 1, pageCount = 3, incognito = false) shouldBe false
        repo.getChapterById(c2.id)!!.let {
            it.lastPageRead shouldBe 1
            it.read shouldBe false
        }

        // A failed page is not saved
        saver.onPageSelected(stored, progress, pageIndex = 2, pageCount = 3, incognito = false, pageFailed = true) shouldBe false
        repo.getChapterById(c2.id)!!.read shouldBe false

        saver.onPageSelected(stored, progress, pageIndex = 2, pageCount = 3, incognito = false) shouldBe true
        repo.getChapterById(c2.id)!!.let {
            it.read shouldBe true
            it.lastPageRead shouldBe 2
        }
        repo.getChapterById(c2dup.id)!!.read shouldBe true
        repo.getChapterById(c1.id)!!.read shouldBe false
        tracked shouldBe listOf(c2.chapterNumber)

        // The last page of an already read chapter completes it again (Android: delete after reading, trackers)
        saver.onPageSelected(stored, progress, pageIndex = 2, pageCount = 3, incognito = false) shouldBe true
        tracked shouldBe listOf(c2.chapterNumber, c2.chapterNumber)

        saver.setBookmark(progress, true)
        repo.getChapterById(c2.id)!!.bookmark shouldBe true

        now += 42_000
        saver.saveHistory(c2.id, incognito = false)
        val history = Injekt.get<GetHistory>().await(stored.id)
        history.single().readDuration shouldBe 42_000
        saver.saveHistory(c1.id, incognito = true)
        Injekt.get<GetHistory>().await(stored.id).size shouldBe 1
        Unit
    }
}
