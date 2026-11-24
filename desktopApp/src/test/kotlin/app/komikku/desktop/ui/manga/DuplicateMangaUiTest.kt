package app.komikku.desktop.ui.manga

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.toSize
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import app.komikku.desktop.ui.browse.FixedSourceManager
import cafe.adriel.voyager.navigator.Navigator
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P10, headless: adding an entry to the library from its details checks [GetDuplicateLibraryManga] first, like
 * Android's `MangaScreenModel.toggleFavorite`. A library entry with the same title from another source opens the
 * duplicate dialog: "Cancel" adds nothing, a click on the duplicate opens its details, "Add anyway" adds the entry; an
 * entry without duplicates is added at once.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DuplicateMangaUiTest {

    private lateinit var app: Application
    private lateinit var existing: Manga
    private lateinit var target: Manga
    private lateinit var addedAnyway: Manga
    private lateinit var unique: Manga

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "duplicate-ui").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        // No sources: the details' first fetch fails quietly (stub source), the stored entries are what is shown
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(emptyList()) }
        existing = TestLibrary.insertManga(SOURCE_A, "/frieren", "Frieren")
        TestLibrary.insertChapters(existing, "/frieren/2" to "Chapter 2", "/frieren/1" to "Chapter 1")
        target = TestLibrary.insertManga(SOURCE_B, "/sousou-no-frieren", "frieren", favorite = false)
        TestLibrary.insertChapters(target, "/sousou-no-frieren/1" to "Chapter 1")
        addedAnyway = TestLibrary.insertManga(SOURCE_C, "/frieren-c", "FRIEREN", favorite = false)
        unique = TestLibrary.insertManga(SOURCE_B, "/dungeon-meshi", "Dungeon Meshi", favorite = false)
        TestLibrary.insertChapters(unique, "/dungeon-meshi/1" to "Chapter 1")
    }

    private fun favorite(manga: Manga) = runBlocking { Injekt.get<GetManga>().await(manga.id)!!.favorite }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.details(manga: Manga) = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme { DesktopToastHost { Navigator(MangaDetailsScreen(manga.id)) } }
        }
    }

    @Test
    fun `a duplicate in the library asks first, then cancel, open or add anyway`() = runDesktopComposeUiTest(width = 1200, height = 900) {
        details(target)
        waitForTag("details-favorite")

        // Cancel: nothing added
        onNodeWithTag("details-favorite").performClick()
        waitForTag("duplicate-dialog")
        onNodeWithTag("duplicate-${existing.id}").assertExists()
        onAllNodesWithText(MR.strings.possible_duplicates_title.localized()).fetchSemanticsNodes().size shouldBe 1
        onNodeWithText(MR.strings.action_cancel.localized()).performClick()
        waitForTag("duplicate-dialog", present = false)
        favorite(target) shouldBe false

        // The duplicate opens the existing entry's details
        onNodeWithTag("details-favorite").performClick()
        waitForTag("duplicate-${existing.id}")
        onNodeWithTag("duplicate-${existing.id}").performClick()
        waitForTag("duplicate-dialog", present = false)
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag("details-title").fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Frieren" } == true
            }
        }
        favorite(target) shouldBe false
    }

    @Test
    fun `add anyway adds the entry`() = runDesktopComposeUiTest(width = 1200, height = 900) {
        details(addedAnyway)
        waitForTag("details-favorite")
        onNodeWithTag("details-favorite").performClick()
        waitForTag("duplicate-${existing.id}")
        onNodeWithTag("duplicate-add-anyway").performClick()
        waitUntil(timeoutMillis = 10_000) { favorite(addedAnyway) }
        waitForTag("duplicate-dialog", present = false)
    }

    @Test
    fun `an entry without duplicates is added at once`() = runDesktopComposeUiTest(width = 1200, height = 900) {
        details(unique)
        waitForTag("details-favorite")
        onNodeWithTag("details-favorite").performClick()
        waitUntil(timeoutMillis = 10_000) { favorite(unique) }
        onAllNodesWithTag("duplicate-dialog").fetchSemanticsNodes().size shouldBe 0
    }

    @Test
    fun `the bulk choices fit a narrow dialog`() = runDesktopComposeUiTest(width = 320, height = 900) {
        setContent {
            CompositionLocalProvider(LocalContext provides app) {
                DesktopTheme {
                    DuplicateMangaDialog(
                        targetManga = target,
                        duplicates = emptyList(),
                        onDismissRequest = {},
                        onConfirm = {},
                        onOpenManga = {},
                        onAllowAllDuplicate = {},
                        onSkipAllDuplicate = {},
                        onSkipDuplicate = {},
                    )
                }
            }
        }
        waitForTag("duplicate-dialog")
        // Unclipped bounds (boundsInRoot is clipped by the parents, so a clipped button would look in place)
        fun bounds(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().let { node ->
            Rect(node.positionInRoot, node.size.toSize())
        }
        val dialog = bounds("duplicate-dialog")
        // Every action is shown whole inside the dialog: none pushed out of it or squeezed until its label breaks over
        // several lines (a single-line text button is 40 dp high; the test density is 1)
        listOf("duplicate-skip", "duplicate-skip-all", "duplicate-allow-all", "duplicate-cancel", "duplicate-add-anyway").forEach { tag ->
            val button = bounds(tag)
            withClue("$tag $button in $dialog") {
                (button.width > 0f) shouldBe true
                (button.height <= 48f) shouldBe true
                (button.left >= dialog.left && button.right <= dialog.right) shouldBe true
                (button.top >= dialog.top && button.bottom <= dialog.bottom) shouldBe true
            }
        }
    }

    private companion object {
        const val SOURCE_A = 9_301L
        const val SOURCE_B = 9_302L
        const val SOURCE_C = 9_303L
    }
}
