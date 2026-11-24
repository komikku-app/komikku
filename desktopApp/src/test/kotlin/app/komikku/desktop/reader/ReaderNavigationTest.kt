package app.komikku.desktop.reader

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.reader.ReaderModel
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
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
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P11 review fixes: the desktop reader's navigation state. The current item is the single source of truth: the
 * current chapter follows it (switches are serialised and re-check where the viewer is), page turns at the end of the
 * items wait for a pending switch instead of loading a chapter, rebuilt item lists keep the viewer on the same item
 * (a removed second half falls back to its page), and entering the previous chapter backwards lands on its last item
 * (the second half of a split last page).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderNavigationTest {

    private lateinit var app: Application
    private val server = FakeMangaServer(pagesPerChapter = 3)
    private lateinit var manga: Manga
    private lateinit var chapters: List<Chapter>
    private val readers = mutableListOf<ReaderModel>()
    private val preferences get() = Injekt.get<ReaderPreferences>()

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "reader-navigation").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeHttpSource(server.baseUrl, chapterCount = 3))) }
        manga = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/navigation", "Navigation manga")
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
        preferences.alwaysShowChapterTransition().set(true)
        preferences.dualPageSplitPaged().set(false)
        preferences.dualPageRotateToFit().set(false)
        runBlocking {
            Injekt.get<ChapterRepository>().updateAll(chapters.map { ChapterUpdate(id = it.id, read = false, lastPageRead = 0) })
        }
    }

    @AfterAll
    fun stop() = server.close()

    private fun chapter(n: Int) = chapters.first { it.url == "/chapter/$n" }

    private suspend fun open(n: Int, page: Int = 0): ReaderModel =
        ReaderModel(manga.id, chapter(n).id, initialPage = page).also {
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

    private fun ReaderModel.layout(): List<String> = state.value.items.map(::describe)

    /** `P<from>` / `N<from>` for transitions, `<chapter>.<page number>` for pages, a `b` suffix for a second half. */
    private fun describe(item: ReaderItem): String = when (item) {
        is InsertPage -> "${item.chapter.chapter.url.removePrefix("/chapter/")}.${item.number}b"
        is ReaderPage -> "${item.chapter.chapter.url.removePrefix("/chapter/")}.${item.number}"
        is ChapterTransition.Prev -> "P${item.from.chapter.url.removePrefix("/chapter/")}"
        is ChapterTransition.Next -> "N${item.from.chapter.url.removePrefix("/chapter/")}"
    }

    private fun ReaderModel.current(): String = describe(state.value.currentItem!!)

    private fun ReaderModel.currentChapterUrl() = state.value.currentChapter?.chapter?.url

    /** The current chapter is the chapter of the current page (no switch left pending). */
    private suspend fun ReaderModel.awaitConsistent() = awaitCondition("current chapter follows the current page") {
        val item = state.value.currentItem
        item !is ReaderPage || item.chapter == state.value.currentChapter
    }

    private fun ReaderModel.indexOf(description: String) = state.value.items.indexOfFirst { describe(it) == description }

    @Test
    fun `next then previous across a chapter boundary leaves the chapter of the shown page current`() = runBlocking<Unit> {
        repeat(3) {
            val reader = open(1)
            awaitCondition("chapter 2 preloaded") { reader.layout() == listOf("P1", "1.1", "1.2", "1.3", "N1", "2.1", "2.2", "2.3") }
            // Onto chapter 2's first page and straight back to chapter 1's last page while the switch runs
            reader.moveTo(reader.indexOf("2.1"))
            reader.moveTo(reader.indexOf("1.3"))
            delay(300)
            reader.awaitConsistent()
            reader.current() shouldBe "1.3"
            reader.currentChapterUrl() shouldBe "/chapter/1"
            reader.close()
            readers.remove(reader)
        }
    }

    @Test
    fun `next at the last item waits for the pending switch instead of loading the chapter again`() = runBlocking<Unit> {
        val reader = open(1)
        awaitCondition("chapter 2 preloaded") { reader.layout() == listOf("P1", "1.1", "1.2", "1.3", "N1", "2.1", "2.2", "2.3") }
        // Chapter 2's last page is the last item until the switch to chapter 2 has rebuilt the items
        reader.moveTo(reader.indexOf("2.3"))
        reader.next()
        delay(300)
        reader.awaitConsistent()
        awaitCondition("on the next transition of chapter 2") { reader.current() == "N2" }
        reader.currentChapterUrl() shouldBe "/chapter/2"
    }

    @Test
    fun `turning split pages off on the second half stays on its page`() = runBlocking<Unit> {
        preferences.dualPageSplitPaged().set(true)
        val reader = open(1)
        awaitCondition("chapter 2 preloaded") { reader.layout().size == 8 }
        val page2 = reader.state.value.items.first { it is ReaderPage && describe(it) == "1.2" } as ReaderPage
        reader.onPageSize(page2, 200, 100)
        awaitCondition("page 2 split") { reader.layout().take(5) == listOf("P1", "1.1", "1.2", "1.2b", "1.3") }
        reader.moveTo(reader.indexOf("1.2b"))
        reader.current() shouldBe "1.2b"
        preferences.dualPageSplitPaged().set(false)
        awaitCondition("page 2 joined") { reader.layout().take(4) == listOf("P1", "1.1", "1.2", "1.3") }
        reader.current() shouldBe "1.2"
    }

    @Test
    fun `entering the previous chapter backwards lands on the second half of its split last page`() = runBlocking<Unit> {
        preferences.dualPageSplitPaged().set(true)
        val reader = open(2)
        val previous = reader.state.value.viewerChapters!!.prevChapter!!
        // Chapter 1's last page is known to be wide (as from an earlier decode), so it is split once loaded
        reader.onPageSize(ReaderPage(2).apply { chapter = previous }, 200, 100)
        reader.current() shouldBe "2.1"
        reader.previous()
        reader.current() shouldBe "P2"
        reader.previous()
        awaitCondition("chapter 1 current") { reader.currentChapterUrl() == "/chapter/1" }
        reader.awaitConsistent()
        reader.current() shouldBe "1.3b"
    }
}
