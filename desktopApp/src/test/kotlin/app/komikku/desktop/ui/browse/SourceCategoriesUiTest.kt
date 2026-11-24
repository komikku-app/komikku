package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.more.SourceCategoriesScreen
import app.komikku.desktop.ui.reader.ReaderLauncher
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.source.service.SourcePreferences
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P21, headless: SY's source categories on desktop — Settings → Browse → "Edit categories" (add, a "|" refused with SY's
 * message, rename, delete with Android's dialogs; stored in `sourcesTabCategories`), and a source put into a category
 * from the sources tab's options dialog ("Categories"), shown under that category's header, and only there while
 * "Filter sources in categories" is on.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourceCategoriesUiTest {

    private lateinit var app: Application
    private val preferences by lazy { Injekt.get<SourcePreferences>() }

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(SimpleSearchSource(id = SOURCE, name = "Gamma source"))) }
        // Independent of the machine's locale (whose language is enabled by default)
        preferences.enabledLanguages().set(setOf("all", "en"))
    }

    @BeforeEach
    fun reset() {
        Injekt.get<ReaderLauncher>().close()
        preferences.sourcesTabCategories().delete()
        preferences.sourcesTabSourcesInCategories().delete()
        preferences.sourcesTabCategoriesFilter().delete()
        preferences.lastUsedSource().delete()
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.waitForText(text: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() == present }

    @Test
    fun `source categories are added, renamed and deleted from the Browse settings`() =
        runDesktopComposeUiTest(width = 1200, height = 1000) {
            setContent { KomikkuApp(context = app) }
            onNodeWithText(MR.strings.label_more.localized()).performClick()
            waitForTag("more-settings")
            onNodeWithTag("more-settings").performClick()
            waitForTag("settings-browse")
            onNodeWithTag("settings-browse").performClick()
            waitForTag("settings-Browse")
            val edit = MR.strings.action_edit_categories.localized()
            onNodeWithTag("settings-Browse").performScrollToNode(hasText(edit))
            onNodeWithText(edit).performClick()

            // Add "Manga"
            waitForTag("source-category-add")
            onNodeWithTag("source-category-add").performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }
            onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Manga")
            onNode(hasText(MR.strings.action_add.localized()) and hasAnyAncestor(isDialog())).performClick()
            waitForTag("source-category-Manga")
            preferences.sourcesTabCategories().get() shouldBe setOf("Manga")

            // A "|" is refused with SY's message
            onNodeWithTag("source-category-add").performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }
            onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("A|B")
            onNode(hasText(MR.strings.action_add.localized()) and hasAnyAncestor(isDialog())).performClick()
            waitForText(SYMR.strings.invalid_category_name.localized())
            preferences.sourcesTabCategories().get() shouldBe setOf("Manga")

            // Rename to "Comics", with a source in it: the source follows
            preferences.sourcesTabSourcesInCategories().set(setOf("$SOURCE|Manga"))
            onNodeWithTag("source-category-rename-Manga").performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }
            onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("Comics")
            onNode(hasText(MR.strings.action_ok.localized()) and hasAnyAncestor(isDialog())).performClick()
            waitForTag("source-category-Comics")
            preferences.sourcesTabCategories().get() shouldBe setOf("Comics")
            preferences.sourcesTabSourcesInCategories().get() shouldBe setOf("$SOURCE|Comics")

            // Delete it (Android's confirmation)
            onNodeWithTag("source-category-delete-Comics").performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText(MR.strings.action_ok.localized()) and hasAnyAncestor(isDialog())).performClick()
            waitForTag("source-category-Comics", present = false)
            preferences.sourcesTabCategories().get() shouldBe emptySet()
            preferences.sourcesTabSourcesInCategories().get() shouldBe emptySet()
        }

    @Test
    fun `a source is put into a category from the sources tab and listed under it`() =
        runDesktopComposeUiTest(width = 1200, height = 1000) {
            preferences.sourcesTabCategories().set(setOf("Favourites", "Later"))
            setContent { KomikkuApp(context = app) }
            onNodeWithText(MR.strings.browse.localized()).performClick()
            waitForTag("source-$SOURCE")

            // Long press → "Categories" → Favourites → OK
            onNodeWithTag("source-$SOURCE").performTouchInput { longClick() }
            waitForTag("source-options-categories")
            onNodeWithTag("source-options-categories").performClick()
            waitForTag("source-categories-Favourites")
            onNodeWithTag("source-categories-Favourites").performClick()
            onNodeWithTag("source-categories-ok").performClick()
            waitUntil(timeoutMillis = 5_000) { preferences.sourcesTabSourcesInCategories().get() == setOf("$SOURCE|Favourites") }

            // Listed under the category (Android: as well as under its language, unless the filter is on)
            waitForTag("source-$SOURCE-category-Favourites")
            waitForTag("source-$SOURCE")
            preferences.sourcesTabCategoriesFilter().set(true)
            waitForTag("source-$SOURCE", present = false)
            waitForTag("source-$SOURCE-category-Favourites")
            onAllNodesWithText("Favourites").fetchSemanticsNodes().isNotEmpty() shouldBe true
        }

    @Test
    fun `the add button leaves the last category's buttons uncovered`() =
        runDesktopComposeUiTest(width = 900, height = 600) {
            val names = (1..25).map { "Cat %02d".format(it) }
            preferences.sourcesTabCategories().set(names.toSet())
            setContent {
                CompositionLocalProvider(LocalContext provides app) {
                    DesktopTheme { Navigator(SourceCategoriesScreen()) { CurrentScreen() } }
                }
            }
            waitForTag("source-category-${names.first()}")
            // To the end of the list (the last row as far up as the list lets it go)
            onNodeWithTag("source-categories").performScrollToIndex(names.lastIndex)
            waitForIdle()
            val delete = onNodeWithTag("source-category-delete-${names.last()}").fetchSemanticsNode().boundsInRoot
            val rename = onNodeWithTag("source-category-rename-${names.last()}").fetchSemanticsNode().boundsInRoot
            val add = onNodeWithTag("source-category-add").fetchSemanticsNode().boundsInRoot
            withClue("delete $delete, rename $rename, add $add") {
                (delete.bottom <= add.top) shouldBe true
                (rename.bottom <= add.top) shouldBe true
            }
        }

    @Test
    fun `a category named like the last used group does not clash with the last used row`() =
        runDesktopComposeUiTest(width = 1200, height = 1000) {
            // The last used copy's key was "<id>-lastused", a category copy's "<id>-<category>": the same row key
            preferences.lastUsedSource().set(SOURCE)
            preferences.sourcesTabCategories().set(setOf("lastused"))
            preferences.sourcesTabSourcesInCategories().set(setOf("$SOURCE|lastused"))
            setContent { KomikkuApp(context = app) }
            onNodeWithText(MR.strings.browse.localized()).performClick()
            waitForTag("source-$SOURCE-lastused")
            waitForTag("source-$SOURCE-category-lastused")
            waitForTag("source-$SOURCE")
        }

    private companion object {
        const val SOURCE = 9_081L
    }
}
