package app.komikku.desktop.reader

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.reader.ReaderModel
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderItem
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P11: the desktop reader shows the neighbouring chapters like Android's viewer adapters. The next chapter is preloaded
 * near the end and its pages follow the "next chapter" transition; reaching one of its pages makes it the current
 * chapter (history of the left chapter saved, the left chapter marked read on its last page, the items rebuilt around
 * the shown page) and back again; "skip read" decides which chapter comes next; without forced transitions the
 * transition between two loaded chapters with nothing missing is left out.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderNeighbourChaptersTest {

    private lateinit var app: Application
    private val server = FakeMangaServer(pagesPerChapter = 3)
    private lateinit var manga: Manga
    private lateinit var chapters: List<Chapter>
    private val readers = mutableListOf<ReaderModel>()
    private val preferences get() = Injekt.get<ReaderPreferences>()

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "reader-neighbours").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource(server.baseUrl, chapterCount = 3))) }
        manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/neighbours", "Neighbours manga")
        chapters = TestLibrary.insertChapters(
            manga,
            "/chapter/3" to "Chapter 3",
            "/chapter/2" to "Chapter 2",
            "/chapter/1" to "Chapter 1",
        )
    }

    @AfterEach
    fun closeReaders() {
        readers.forEach { it.close() }
        readers.clear()
        preferences.skipRead().set(false)
        preferences.alwaysShowChapterTransition().set(true)
        // Each test starts from unread chapters
        runBlocking {
            Injekt.get<ChapterRepository>().updateAll(chapters.map { ChapterUpdate(id = it.id, read = false, lastPageRead = 0) })
        }
    }

    @AfterAll
    fun stop() = server.close()

    private fun chapter(n: Int) = chapters.first { it.url == "/chapter/$n" }

    private fun stored(n: Int) = runBlocking { Injekt.get<ChapterRepository>().getChapterById(chapter(n).id)!! }

    private suspend fun open(n: Int): ReaderModel =
        ReaderModel(manga.id, chapter(n).id, initialPage = 0).also {
            readers += it
            it.init().getOrThrow()
        }

    private suspend fun awaitCondition(what: String, condition: suspend () -> Boolean) {
        try {
            withTimeout(15_000) { while (!condition()) delay(20) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for: $what", e)
        }
    }

    /** `P<from>` / `N<from>` for transitions, `<chapter>.<page number>` for pages. */
    private fun ReaderModel.layout(): List<String> = state.value.items.map(::describe)

    private fun describe(item: ReaderItem): String = when (item) {
        is ReaderPage -> "${item.chapter.chapter.url.removePrefix("/chapter/")}.${item.number}"
        is ChapterTransition.Prev -> "P${item.from.chapter.url.removePrefix("/chapter/")}"
        is ChapterTransition.Next -> "N${item.from.chapter.url.removePrefix("/chapter/")}"
    }

    private fun ReaderModel.current(): String = describe(state.value.currentItem!!)

    private fun ReaderModel.currentChapterUrl() = state.value.currentChapter?.chapter?.url

    @Test
    fun `the next chapter follows its transition, reaching it switches chapters and back`() = runBlocking<Unit> {
        val reader = open(1)
        // Three pages: page 1 is already within Android's preload range, chapter 2 is appended after its transition
        awaitCondition("chapter 2 preloaded") { reader.layout() == listOf("P1", "1.1", "1.2", "1.3", "N1", "2.1", "2.2", "2.3") }
        reader.current() shouldBe "1.1"

        reader.next()
        reader.next()
        reader.current() shouldBe "1.3"
        awaitCondition("chapter 1 read on its last page") { stored(1).read }
        reader.next()
        reader.current() shouldBe "N1"
        reader.next()
        // On chapter 2's first page: it is the current chapter now, the items are rebuilt around that page
        awaitCondition("chapter 2 current") { reader.currentChapterUrl() == "/chapter/2" }
        reader.current() shouldBe "2.1"
        awaitCondition("chapter 3 preloaded") {
            reader.layout() == listOf("1.1", "1.2", "1.3", "P2", "2.1", "2.2", "2.3", "N2", "3.1", "3.2", "3.3")
        }
        reader.state.value.currentPageNumber shouldBe 1
        awaitCondition("history of chapter 1") {
            Injekt.get<GetHistory>().await(manga.id).any { it.chapterId == chapter(1).id }
        }
        // Progress is saved for the chapter the page belongs to
        reader.next()
        reader.current() shouldBe "2.2"
        awaitCondition("progress of chapter 2") { stored(2).lastPageRead == 1L && !stored(2).read }

        // Back through the transition onto chapter 1's last page: chapter 1 is current again
        reader.previous()
        reader.previous()
        reader.current() shouldBe "P2"
        reader.previous()
        reader.current() shouldBe "1.3"
        awaitCondition("chapter 1 current again") { reader.currentChapterUrl() == "/chapter/1" }
        reader.current() shouldBe "1.3"
        reader.layout() shouldBe listOf("P1", "1.1", "1.2", "1.3", "N1", "2.1", "2.2", "2.3")

        // The slider / first-page jump addresses the current chapter's pages
        reader.moveToFirstPage()
        reader.current() shouldBe "1.1"
    }

    @Test
    fun `skip read decides the next chapter, its pages follow`() = runBlocking<Unit> {
        Injekt.get<ChapterRepository>().update(ChapterUpdate(id = chapter(2).id, read = true))
        preferences.skipRead().set(true)
        try {
            val reader = open(1)
            reader.state.value.viewerChapters!!.nextChapter!!.chapter.url shouldBe "/chapter/3"
            awaitCondition("chapter 3 preloaded") { reader.layout().takeLast(4) == listOf("N1", "3.1", "3.2", "3.3") }
        } finally {
            Injekt.get<ChapterRepository>().update(ChapterUpdate(id = chapter(2).id, read = false))
        }
    }

    @Test
    fun `without forced transitions loaded neighbours follow each other directly`() = runBlocking<Unit> {
        preferences.alwaysShowChapterTransition().set(false)
        val reader = open(1)
        awaitCondition("chapter 2 preloaded") { reader.layout() == listOf("P1", "1.1", "1.2", "1.3", "2.1", "2.2", "2.3") }
        reader.moveTo(3)
        reader.current() shouldBe "1.3"
        reader.next()
        awaitCondition("chapter 2 current") { reader.currentChapterUrl() == "/chapter/2" }
        reader.current() shouldBe "2.1"
        awaitCondition("chapter 3 preloaded") {
            reader.layout() == listOf("1.1", "1.2", "1.3", "2.1", "2.2", "2.3", "3.1", "3.2", "3.3")
        }
    }

    @Test
    fun `the transition stays while it is shown when the neighbour finishes loading`() = runBlocking<Unit> {
        preferences.alwaysShowChapterTransition().set(false)
        val reader = open(3)
        // Chapter 3 is the last one; its previous chapter (2) is loaded once its transition is reached
        reader.layout() shouldBe listOf("P3", "3.1", "3.2", "3.3", "N3")
        reader.previous()
        reader.current() shouldBe "P3"
        awaitCondition("chapter 2 preloaded") { reader.layout() == listOf("2.1", "2.2", "2.3", "P3", "3.1", "3.2", "3.3", "N3") }
        reader.current() shouldBe "P3"
        reader.previous()
        reader.current() shouldBe "2.3"
        awaitCondition("chapter 2 current") { reader.currentChapterUrl() == "/chapter/2" }
    }
}
