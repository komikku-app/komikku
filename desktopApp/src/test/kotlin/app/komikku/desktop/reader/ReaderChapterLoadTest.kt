package app.komikku.desktop.reader

import android.app.Application
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.reader.ReaderModel
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * P12 review fixes: the desktop reader loads a chapter's page list once, whoever asks for it. A preload suspended in
 * the source while the chapter is opened (the "next chapter" button, a page turn past the transition) is joined, not
 * repeated, so the items keep the pages the chapter has.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReaderChapterLoadTest {

    private lateinit var app: Application
    private val server = FakeMangaServer(pagesPerChapter = 3)
    private val source by lazy { GatedHttpSource(server.baseUrl) }
    private lateinit var manga: Manga
    private lateinit var chapters: List<Chapter>
    private val readers = mutableListOf<ReaderModel>()

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "reader-chapter-load").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(source)) }
        manga = TestLibrary.insertManga(GatedHttpSource.ID, "/manga/chapter-load", "Chapter load manga")
        chapters = TestLibrary.insertChapters(
            manga,
            "/chapter/3" to "Chapter 3",
            "/chapter/2" to "Chapter 2",
            "/chapter/1" to "Chapter 1",
        )
    }

    @BeforeEach
    fun clearPageListCache() {
        // A page list read once is served from the chapter cache afterwards, without asking the source
        Injekt.get<ChapterCache>().clear()
    }

    @AfterEach
    fun closeReaders() {
        source.gates.values.forEach { it.complete(Unit) }
        readers.forEach { it.close() }
        readers.clear()
        source.gates.clear()
        source.calls.clear()
    }

    @AfterAll
    fun stop() = server.close()

    private fun chapter(n: Int) = chapters.first { it.url == "/chapter/$n" }

    private suspend fun awaitCondition(what: String, condition: suspend () -> Boolean) {
        try {
            withTimeout(15_000) { while (!condition()) delay(20) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for: $what", e)
        }
    }

    @Test
    fun `opening the next chapter while its preload is suspended joins the preload`() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        source.gates["/chapter/2"] = gate
        val reader = ReaderModel(manga.id, chapter(1).id).also { readers += it }
        // Chapter 1 has 3 pages: opening it on its first page preloads chapter 2, which waits in the source
        reader.init().getOrThrow()
        awaitCondition("the preload of chapter 2 in the source") { source.calls("/chapter/2") == 1 }

        // The second caller arrives while the first load is suspended
        reader.loadNextChapter()
        awaitCondition("the adjacent load joined the preload") { reader.joinedChapterLoads.value >= 1 }
        source.calls("/chapter/2") shouldBe 1

        gate.complete(Unit)
        awaitCondition("chapter 2 opened") {
            reader.state.value.currentChapter?.chapter?.url == "/chapter/2" && !reader.state.value.isLoadingAdjacent
        }
        delay(200)
        source.calls("/chapter/2") shouldBe 1
        // The items hold the very pages chapter 2 has
        val current = reader.state.value.currentChapter!!
        val shown = reader.state.value.items.filterIsInstance<ReaderPage>().filter { it.chapter == current }
        shown.size shouldBe 3
        shown.all { page -> current.pages!!.any { it === page } } shouldBe true
    }

    @Test
    fun `a preload and a page turn past the transition load the chapter once`() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        source.gates["/chapter/1"] = gate
        // Chapter 2 opened on its first page; chapter 1, the previous chapter, is preloaded once its transition is reached
        val reader = ReaderModel(manga.id, chapter(2).id).also { readers += it }
        reader.init().getOrThrow()
        reader.previous()
        awaitCondition("the preload of chapter 1 in the source") { source.calls("/chapter/1") == 1 }
        // On the "previous chapter" transition, whose chapter has no pages yet: the turn loads it
        reader.previous()
        awaitCondition("the page turn joined the preload") { reader.joinedChapterLoads.value >= 1 }

        gate.complete(Unit)
        awaitCondition("chapter 1 current") {
            reader.state.value.currentChapter?.chapter?.url == "/chapter/1" && !reader.state.value.isLoadingAdjacent
        }
        delay(200)
        source.calls("/chapter/1") shouldBe 1
        val current = reader.state.value.currentChapter!!
        val shown = reader.state.value.items.filterIsInstance<ReaderPage>().filter { it.chapter == current }
        shown.all { page -> current.pages!!.any { it === page } } shouldBe true
        // Entered backwards on its last page
        (reader.state.value.currentItem as ReaderPage).number shouldBe 3
    }
}

/**
 * [FakeHttpSource] whose page lists wait for a gate per chapter URL ([gates]) and are counted ([calls]): a load held
 * in the source while a second caller arrives.
 */
class GatedHttpSource(override val baseUrl: String) : HttpSource() {
    override val name = "Gated online"
    override val lang = "en"
    override val supportsLatest = false
    override val id: Long = ID

    val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    val calls = ConcurrentHashMap<String, AtomicInteger>()

    fun calls(url: String): Int = calls[url]?.get() ?: 0

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        SManga.create().apply {
            url = manga.url
            title = manga.title
            initialized = true
        },
        emptyList(),
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        calls.getOrPut(chapter.url) { AtomicInteger() }.incrementAndGet()
        gates[chapter.url]?.await()
        return (1..3).map { Page(it - 1, imageUrl = "$baseUrl/img${chapter.url.removePrefix("/chapter")}/$it.png") }
    }

    override fun pageListParse(response: Response): List<Page> = error("not used")

    companion object {
        const val ID = 9103L
    }
}
