package app.komikku.desktop.ui

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.FakeHttpSource
import app.komikku.desktop.reader.FakeMangaServer
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.DesktopToastHost
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.browse.SimpleSearchSource
import app.komikku.desktop.window.DesktopShortcuts
import app.komikku.shared.library.LibraryUpdateProgress
import app.komikku.shared.library.LibraryUpdateRunner
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Headless UI test of desktop parity step P2: library grouping through the settings dialog's Group tab and the same
 * `groupLibraryBy` preference as Android (by source, status, ungrouped; tracking status only with a tracker), and the
 * library selection (long press, click, Shift-click range, select all, invert) with its batch actions (mark read /
 * unread, set categories, download, remove from the library with the downloaded chapters).
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryGroupSelectionUiTest {

    private lateinit var app: Application
    private val server = FakeMangaServer(pagesPerChapter = 1)
    private lateinit var ajin: Manga
    private lateinit var blue: Manga
    private lateinit var frieren: Manga
    private lateinit var meshi: Manga
    private var reading = 0L
    private var later = 0L
    private val runner = RecordingUpdateRunner()

    @BeforeAll
    fun boot() {
        // Not a @TempDir: background download-cache writes may still run when JUnit would delete it.
        val dataDir = File(System.getProperty("komikku.dataDir"), "library-group-selection").apply { mkdirs() }
        app = DesktopInjekt.init(dataDir)
        Injekt.addSingletonFactory<SourceManager> {
            FixedSourceManager(listOf(FakeHttpSource(server.baseUrl), SimpleSearchSource(id = SECOND_SOURCE, name = "Second source")))
        }
        Injekt.addSingletonFactory<LibraryUpdateRunner> { runner }
        Injekt.get<DownloadPreferences>().saveChaptersAsCBZ().set(false)
        runBlocking {
            val categories = Injekt.get<CategoryRepository>()
            reading = categories.insert(Category(id = 0, name = "Reading", order = 1, flags = 0, hidden = false))
            later = categories.insert(Category(id = 0, name = "Later", order = 2, flags = 0, hidden = false))
            ajin = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/ajin", "Ajin")
            blue = TestLibrary.insertManga(FakeHttpSource.ID, "/manga/blue", "Blue Period")
            frieren = TestLibrary.insertManga(SECOND_SOURCE, "/manga/frieren", "Frieren")
            meshi = TestLibrary.insertManga(SECOND_SOURCE, "/manga/meshi", "Dungeon Meshi")
            TestLibrary.insertChapters(ajin, "/chapter/2" to "Chapter 2", "/chapter/1" to "Chapter 1")
            val updateManga = Injekt.get<UpdateManga>()
            updateManga.await(MangaUpdate(id = ajin.id, status = SManga.ONGOING.toLong()))
            updateManga.await(MangaUpdate(id = blue.id, status = SManga.COMPLETED.toLong()))
            val mangaRepository = Injekt.get<MangaRepository>()
            listOf(ajin, blue, frieren).forEach { mangaRepository.setMangaCategories(it.id, listOf(reading)) }
            mangaRepository.setMangaCategories(meshi.id, listOf(later))
        }
        Injekt.get<LibraryPreferences>().groupLibraryBy().set(LibraryGroup.BY_DEFAULT)
    }

    @AfterAll
    fun stop() = server.close()

    @Test
    fun `group the library by source, status and ungrouped`() = runDesktopComposeUiTest(width = 1200, height = 800) {
        val preferences = Injekt.get<LibraryPreferences>()
        setContent { KomikkuApp(context = app, shortcuts = DesktopShortcuts(isMacOs = false)) }
        waitForText("Ajin")

        // Group tab of the settings dialog: by source (no tracking status without a logged-in tracker)
        onNodeWithTag("library-settings").performClick()
        onNodeWithTag("library-settings-tab-3").performClick()
        onAllNodesWithTag("library-group-${LibraryGroup.BY_TRACK_STATUS}").fetchSemanticsNodes().size shouldBe 0
        onNodeWithTag("library-group-${LibraryGroup.BY_SOURCE}").performClick()
        waitUntil(timeoutMillis = 5_000) { preferences.groupLibraryBy().get() == LibraryGroup.BY_SOURCE }
        onNodeWithText(MR.strings.action_close.localized()).performClick()

        // One page per source, named after it
        waitForTag("library-tab-$SECOND_SOURCE")
        onNodeWithTag("library-tab-${FakeHttpSource.ID}").assertExists()
        onNodeWithTag("library-tab-$SECOND_SOURCE").performClick()
        waitForTag("library-item-${meshi.id}")
        onNodeWithTag("library-item-${frieren.id}").assertExists()
        onAllNodesWithTag("library-item-${ajin.id}").fetchSemanticsNodes().size shouldBe 0

        // By status (Android's status groups and order; the shown group survives only by id)
        runOnIdle { preferences.groupLibraryBy().set(LibraryGroup.BY_STATUS) }
        waitForText(MR.strings.ongoing.localized())
        waitForText(MR.strings.completed.localized())
        waitForText(MR.strings.unknown.localized())
        onNodeWithTag("library-tab-${SManga.ONGOING + 1}").performClick()
        waitForTag("library-item-${ajin.id}")
        onAllNodesWithTag("library-item-${blue.id}").fetchSemanticsNodes().size shouldBe 0

        // Ungrouped: one page with everything, no tabs
        runOnIdle { preferences.groupLibraryBy().set(LibraryGroup.UNGROUPED) }
        waitForTag("library-item-${meshi.id}")
        listOf(ajin, blue, frieren).forEach { onNodeWithTag("library-item-${it.id}").assertExists() }
        onAllNodesWithText(SYMR.strings.ungrouped.localized()).fetchSemanticsNodes().size shouldBe 0

        runOnIdle { preferences.groupLibraryBy().set(LibraryGroup.BY_DEFAULT) }
        waitForText("Reading")
    }

    @Test
    fun `select entries and run the batch actions`() = runDesktopComposeUiTest(width = 1200, height = 800) {
        setContent { KomikkuApp(context = app, shortcuts = DesktopShortcuts(isMacOs = false)) }
        waitForTag("library-item-${ajin.id}")

        // Long press starts the selection; Shift-click selects the range (Ajin … Frieren on "Reading")
        onNodeWithTag("library-item-${ajin.id}").performTouchInput { longClick() }
        waitForTag("library-selection-toolbar")
        selectionCount() shouldBe "1"
        onNodeWithTag("library-item-${ajin.id}").assertIsSelected()
        onNodeWithTag("library-item-${frieren.id}").performKeyInput { keyDown(Key.ShiftLeft) }
        onNodeWithTag("library-item-${frieren.id}").performMouseInput { click() }
        onNodeWithTag("library-item-${frieren.id}").performKeyInput { keyUp(Key.ShiftLeft) }
        waitUntil(timeoutMillis = 5_000) { selectionCount() == "3" }

        // A click toggles while selecting; invert and select all work on the shown page
        onNodeWithTag("library-item-${blue.id}").performClick()
        waitUntil(timeoutMillis = 5_000) { selectionCount() == "2" }
        onNodeWithContentDescription(MR.strings.action_select_inverse.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { selectionCount() == "1" }
        onNodeWithTag("library-item-${blue.id}").assertIsSelected()
        onNodeWithContentDescription(MR.strings.action_select_all.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { selectionCount() == "3" }
        onNodeWithContentDescription(MR.strings.action_cancel.localized()).performClick()
        waitForTag("library-selection-toolbar", present = false)

        // Mark read / unread
        select(ajin)
        onNodeWithContentDescription(MR.strings.action_mark_as_read.localized()).performClick()
        waitForTag("library-selection-toolbar", present = false)
        waitUntil(timeoutMillis = 5_000) { chapters(ajin).all { it.read } }
        select(ajin)
        onNodeWithContentDescription(MR.strings.action_mark_as_unread.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { chapters(ajin).none { it.read } }

        // Set categories: the dialog starts with the common category checked; add "Later"
        select(blue)
        onNodeWithContentDescription(MR.strings.action_move_category.localized()).performClick()
        waitForText(MR.strings.action_ok.localized())
        onNode(hasText("Later") and hasAnyAncestor(isDialog())).performClick()
        onNodeWithText(MR.strings.action_ok.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { categoriesOf(blue) == setOf("Reading", "Later") }
        waitForTag("library-selection-toolbar", present = false)

        // Download the unread chapters
        select(ajin)
        onNodeWithContentDescription(MR.strings.action_download.localized()).performClick()
        onNodeWithText(MR.strings.download_unread.localized()).performClick()
        waitForTag("library-selection-toolbar", present = false)
        waitUntil(timeoutMillis = 20_000) { chapters(ajin).all { isDownloaded(it) } }

        // Remove from the library with the downloaded chapters
        select(ajin)
        onNodeWithContentDescription(MR.strings.action_delete.localized()).performClick()
        onNodeWithText(MR.strings.manga_from_library.localized()).performClick()
        onNodeWithText(MR.strings.downloaded_chapters.localized()).performClick()
        onNodeWithText(MR.strings.action_ok.localized()).performClick()
        waitUntil(timeoutMillis = 10_000) { runBlocking { Injekt.get<GetManga>().await(ajin.id) }?.favorite == false }
        waitUntil(timeoutMillis = 10_000) { chapters(ajin).none { isDownloaded(it) } }
        waitForTag("library-item-${ajin.id}", present = false)
        runBlocking { Injekt.get<GetManga>().await(blue.id) }!!.favorite shouldBe true
        categoriesOf(frieren) shouldContainExactlyInAnyOrder setOf("Reading")
    }

    @Test
    fun `update targets of group pages and selections, and an update already running`() = runDesktopComposeUiTest(width = 1200, height = 800) {
        val preferences = Injekt.get<LibraryPreferences>()
        setContent { DesktopToastHost { KomikkuApp(context = app, shortcuts = DesktopShortcuts(isMacOs = false)) } }
        // Not Ajin / Blue Period: the batch-action test may have removed or moved them already
        waitForTag("library-item-${frieren.id}")
        try {
            // A source page: Android SY's grouped update (restrictions apply), not the restriction-free entries
            runOnIdle { preferences.groupLibraryBy().set(LibraryGroup.BY_SOURCE) }
            waitForTag("library-tab-$SECOND_SOURCE")
            onNodeWithTag("library-tab-$SECOND_SOURCE").performClick()
            waitForTag("library-item-${meshi.id}")
            onNodeWithTag("update-category").performClick()
            runner.targets.last() shouldBe LibraryUpdateRunner.Target.Group(LibraryGroup.BY_SOURCE, setOf(frieren.id, meshi.id))

            // A category filter with included categories shows one ungrouped page: its update is the ungrouped
            // group's, not category 0's
            runOnIdle {
                preferences.groupLibraryBy().set(LibraryGroup.BY_DEFAULT)
                preferences.filterCategoriesInclude().set(setOf(later.toString()))
                preferences.filterCategories().set(true)
            }
            waitForTag("library-item-${frieren.id}", present = false)
            waitForTag("library-item-${meshi.id}")
            onNodeWithTag("update-category").performClick()
            val ungrouped = runner.targets.last() as LibraryUpdateRunner.Target.Group
            ungrouped.groupType shouldBe LibraryGroup.UNGROUPED
            (meshi.id in ungrouped.mangaIds && frieren.id !in ungrouped.mangaIds) shouldBe true
            runOnIdle {
                preferences.filterCategories().set(false)
                preferences.filterCategoriesInclude().set(emptySet())
            }
            waitForTag("library-item-${frieren.id}")

            // "Update" on a selection while an update runs: the selection stays and the user is told
            runner.running = true
            select(frieren)
            onNodeWithContentDescription(KMR.strings.action_update.localized()).performClick()
            waitForText(MR.strings.update_already_running.localized())
            runner.targets.last() shouldBe LibraryUpdateRunner.Target.Entries(setOf(frieren.id))
            onNodeWithTag("library-selection-count").assertTextEquals("1")
            runner.running = false
            onNodeWithContentDescription(KMR.strings.action_update.localized()).performClick()
            waitForTag("library-selection-toolbar", present = false)

            // The selection bar stays on screen when the shown page is empty
            select(frieren)
            runOnIdle { preferences.filterBookmarked().set(TriState.ENABLED_IS) }
            waitForTag("library-item-${frieren.id}", present = false)
            onNodeWithTag("library-selection-bar").assertIsDisplayed()
            onNodeWithContentDescription(MR.strings.action_cancel.localized()).performClick()
            waitForTag("library-selection-toolbar", present = false)
        } finally {
            runner.running = false
            preferences.filterBookmarked().set(TriState.DISABLED)
            preferences.filterCategories().set(false)
            preferences.filterCategoriesInclude().set(emptySet())
            preferences.groupLibraryBy().set(LibraryGroup.BY_DEFAULT)
        }
    }

    /** Records the started targets; reports "already running" while [running]. */
    private class RecordingUpdateRunner : LibraryUpdateRunner {
        val targets = mutableListOf<LibraryUpdateRunner.Target>()

        @Volatile
        var running = false
        override val progress = MutableStateFlow<LibraryUpdateProgress?>(null)

        override fun start(target: LibraryUpdateRunner.Target): Boolean {
            targets += target
            return !running
        }

        override fun cancel() = Unit
    }

    private fun ComposeUiTest.select(manga: Manga) {
        onNodeWithTag("library-item-${manga.id}").performTouchInput { longClick() }
        waitForTag("library-selection-toolbar")
        onNodeWithTag("library-selection-count").assertTextEquals("1")
    }

    private fun ComposeUiTest.selectionCount(): String? =
        onAllNodesWithTag("library-selection-count").fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    private fun chapters(manga: Manga): List<Chapter> = runBlocking { Injekt.get<ChapterRepository>().getChapterByMangaId(manga.id) }

    private fun isDownloaded(chapter: Chapter): Boolean =
        Injekt.get<DownloadManager>().isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, ajin.ogTitle, ajin.source)

    private fun categoriesOf(manga: Manga): Set<String> =
        runBlocking { Injekt.get<GetCategories>().await(manga.id) }.map { it.name }.toSet()

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private companion object {
        const val SECOND_SOURCE = 7301L
    }
}
