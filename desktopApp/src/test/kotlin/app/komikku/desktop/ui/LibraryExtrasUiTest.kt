package app.komikku.desktop.ui

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.extensions.ExtensionIconDrawable
import app.komikku.desktop.reader.TestImages
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.desktop.ui.tabs.SourceIconBadge
import app.komikku.desktop.ui.tabs.SourceIcons
import app.komikku.desktop.window.DesktopShortcuts
import eu.kanade.tachiyomi.extension.model.Extension
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P12, headless: the library extras of Android's library. The "continue reading" button (`showContinueReadingButton`)
 * on entries with unread chapters opens the next unread chapter in the reader, in the grid and the list; "Open random
 * entry" opens one of the shown page's entries (Android's message when the page is empty); the source badge
 * (`sourceBadge`, a warning for a missing source) follows its preference.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryExtrasUiTest {

    private lateinit var app: Application
    private lateinit var unread: Manga
    private lateinit var read: Manga
    private lateinit var chapters: List<Chapter>

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        unread = TestLibrary.insertManga(SOURCE_ID, "/unread", "Unread entry")
        read = TestLibrary.insertManga(SOURCE_ID, "/read", "Finished entry")
        chapters = TestLibrary.insertChapters(unread, "/unread/2" to "Chapter 2", "/unread/1" to "Chapter 1")
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) = waitUntil(timeoutMillis = 15_000) {
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() == present
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `continue reading opens the next unread chapter, in the grid and the list`() = runDesktopComposeUiTest(width = 1200, height = 800) {
        val preferences = Injekt.get<LibraryPreferences>()
        preferences.showContinueReadingButton().set(true)
        preferences.displayMode().set(LibraryDisplayMode.CompactGrid)
        val launcher = Injekt.get<ReaderLauncher>()
        launcher.close()
        setContent { KomikkuApp(context = app, shortcuts = DesktopShortcuts(isMacOs = false)) }
        waitForText("Unread entry")
        waitForTag("continue-reading-${unread.id}")
        // Only entries with unread chapters have the button
        onAllNodesWithTag("continue-reading-${read.id}", useUnmergedTree = true).assertCountEquals(0)

        onNodeWithTag("continue-reading-${unread.id}", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 10_000) { launcher.request.value != null }
        launcher.request.value!!.chapterId shouldBe chapters.first { it.url == "/unread/1" }.id
        runOnIdle { launcher.close() }

        // The list has it too; the preference off hides it
        runOnIdle { preferences.displayMode().set(LibraryDisplayMode.List) }
        waitForTag("library-list")
        waitForTag("continue-reading-${unread.id}")
        runOnIdle { preferences.showContinueReadingButton().set(false) }
        waitForTag("continue-reading-${unread.id}", present = false)
        preferences.displayMode().set(LibraryDisplayMode.CompactGrid)
    }

    @Test
    fun `random entry opens one of the shown entries, a message when there are none`() = runDesktopComposeUiTest(width = 1200, height = 800) {
        Injekt.get<ReaderLauncher>().close()
        setContent { KomikkuApp(context = app, shortcuts = DesktopShortcuts(isMacOs = false)) }
        waitForText("Unread entry")
        // Only one entry shown: the random entry is that one
        onNodeWithTag("library-search").performTextInput("Finished entry")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Unread entry").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("library-random").performClick()
        waitForTag("details-edit-info")
        waitForText("Finished entry")
        onNodeWithContentDescription(MR.strings.action_bar_up_description.localized()).performClick()

        // Nothing shown: Android's message
        waitForTag("library-search")
        onNodeWithTag("library-search").performTextClearance()
        onNodeWithTag("library-search").performTextInput("zzz")
        waitForText(MR.strings.no_results_found.localized())
        onNodeWithTag("library-random").performClick()
        waitForText(MR.strings.information_no_entries_found.localized())
    }

    @Test
    fun `the source badge follows its preference`() = runDesktopComposeUiTest(width = 1200, height = 800) {
        val preferences = Injekt.get<LibraryPreferences>()
        preferences.sourceBadge().set(true)
        Injekt.get<ReaderLauncher>().close()
        setContent { KomikkuApp(context = app, shortcuts = DesktopShortcuts(isMacOs = false)) }
        waitForText("Unread entry")
        // The test source is not installed: Android's warning badge for a missing source
        waitForTag("source-badge-${unread.id}")
        runOnIdle { preferences.sourceBadge().set(false) }
        waitForTag("source-badge-${unread.id}", present = false)
        preferences.sourceBadge().set(true)
    }

    @Test
    fun `the source badge shows an extension's icon once the extensions are loaded`() = runDesktopComposeUiTest(width = 400, height = 200) {
        // P12 review: the extensions load after the library is shown; the badge must not keep the missing icon
        val installed = MutableStateFlow(emptyList<Extension.Installed>())
        val source = Source(id = SOURCE_ID, lang = "en", name = "Later source", supportsLatest = false, isStub = true)
        setContent { SourceIconBadge(source, Modifier.testTag("badge"), installed) }
        waitUntil(timeoutMillis = 15_000) { badgeKind() == SourceIcons.MISSING }
        installed.value = listOf(
            Extension.Installed(
                name = "Later",
                pkgName = "app.komikku.test.later",
                versionName = "1.0",
                versionCode = 1,
                libVersion = 1.4,
                lang = "en",
                isNsfw = false,
                signatureHash = "test",
                pkgFactory = null,
                sources = listOf(StubSource(SOURCE_ID, "en", "Later source")),
                icon = ExtensionIconDrawable(TestImages.encode(16, 16)),
                isShared = false,
            ),
        )
        waitUntil(timeoutMillis = 15_000) { badgeKind() == SourceIcons.ICON }
    }

    private fun ComposeUiTest.badgeKind(): String? = onAllNodesWithTag("badge", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .firstOrNull()
        ?.config
        ?.getOrNull(SemanticsProperties.StateDescription)

    private companion object {
        const val SOURCE_ID = 4712L
    }
}
