package app.komikku.desktop.ui.migration

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import app.komikku.desktop.ui.browse.FixedSourceManager
import app.komikku.desktop.ui.browse.SimpleSearchSource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrateSourceScreenModel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P20, headless: Android's Browse → Migrate on desktop — the sources with library entries and their counts (a source
 * that is not installed first and marked, non-library entries not counted), sorting by name / count in both directions
 * (stored in Android's preferences), the source search by name or id; a source's library entries by title, one opening
 * the migration configuration, the selection (long press, click, invert, select all) opening it for the selection and
 * from there the migration list.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrowseMigrateUiTest {

    private lateinit var app: Application
    private val preferences by lazy { Injekt.get<SourcePreferences>() }
    private var navigator: Navigator? = null
    private lateinit var eta: Manga
    private lateinit var theta: Manga
    private lateinit var zeta: Manga

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> {
            FixedSourceManager(listOf(SimpleSearchSource(ALPHA, "Alpha source"), SimpleSearchSource(BETA, "Beta source")))
        }
        zeta = TestLibrary.insertManga(ALPHA, "/zeta", "Zeta")
        eta = TestLibrary.insertManga(ALPHA, "/eta", "eta")
        theta = TestLibrary.insertManga(ALPHA, "/theta", "Theta")
        TestLibrary.insertManga(BETA, "/mu", "Mu")
        TestLibrary.insertManga(BETA, "/omega", "Omega", favorite = false)
        TestLibrary.insertManga(STUB, "/lost", "Lost")
    }

    @BeforeEach
    fun reset() {
        Injekt.get<SetMigrateSorting>().await(SetMigrateSorting.Mode.ALPHABETICAL, SetMigrateSorting.Direction.ASCENDING)
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.show() = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme {
                DesktopToastHost {
                    Navigator(MigrateHost) {
                        navigator = it
                        CurrentScreen()
                    }
                }
            }
        }
    }

    /** The rows with these tags from top to bottom (the ones shown). */
    private fun ComposeUiTest.order(vararg tags: String): List<String> = tags
        .mapNotNull { tag -> onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.let { tag to it.boundsInRoot.top } }
        .sortedBy { it.second }
        .map { it.first }

    private fun ComposeUiTest.waitForOrder(vararg tags: String) =
        waitUntil(timeoutMillis = 15_000) { order(*tags) == tags.toList() }

    private val sourceRows = arrayOf("migrate-source-$STUB", "migrate-source-$ALPHA", "migrate-source-$BETA")

    @Test
    fun `the sources with library entries are listed with their counts, sorted and searched`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            show()
            // Alphabetical, ascending: the source that is not installed first (shown by its id), then by name
            waitForOrder("migrate-source-$STUB", "migrate-source-$ALPHA", "migrate-source-$BETA")
            onAllNodes(hasText("3") and hasTestTag("migrate-source-count-$ALPHA"), useUnmergedTree = true).fetchSemanticsNodes().size shouldBe 1
            onAllNodes(hasText("1") and hasTestTag("migrate-source-count-$BETA"), useUnmergedTree = true).fetchSemanticsNodes().size shouldBe 1
            val stubRow = hasAnyAncestor(hasTestTag("migrate-source-$STUB"))
            onAllNodes(hasText(MR.strings.not_installed.localized()) and stubRow, useUnmergedTree = true).fetchSemanticsNodes().size shouldBe 1
            onAllNodes(hasText("$STUB") and stubRow, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() shouldBe true

            // By count, ascending (not installed still first), then descending (reversed, not installed still first)
            onNodeWithTag("migrate-sources-sort-mode").performClick()
            waitForOrder("migrate-source-$STUB", "migrate-source-$BETA", "migrate-source-$ALPHA")
            onNodeWithTag("migrate-sources-sort-direction").performClick()
            waitForOrder("migrate-source-$STUB", "migrate-source-$ALPHA", "migrate-source-$BETA")
            preferences.migrationSortingMode().get() shouldBe SetMigrateSorting.Mode.TOTAL
            preferences.migrationSortingDirection().get() shouldBe SetMigrateSorting.Direction.DESCENDING

            // Android's source search: by name, by id
            onNodeWithTag("migrate-sources-search").performTextInput("beta")
            waitUntil(timeoutMillis = 15_000) { order(*sourceRows) == listOf("migrate-source-$BETA") }
            onNodeWithTag("migrate-sources-search").performTextReplacement("$ALPHA")
            waitUntil(timeoutMillis = 15_000) { order(*sourceRows) == listOf("migrate-source-$ALPHA") }
        }

    @Test
    fun `a source's library entries open the configuration alone or as a selection, and from there the list`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            show()
            waitForTag("migrate-source-$ALPHA")
            onNodeWithTag("migrate-source-$ALPHA").performClick()
            // By title, ignoring case; the non-library entry and the other sources' entries are not there
            waitForOrder("migrate-manga-${eta.id}", "migrate-manga-${theta.id}", "migrate-manga-${zeta.id}")
            onAllNodesWithText("Mu").fetchSemanticsNodes().isEmpty() shouldBe true

            // A click opens the configuration for that entry
            onNodeWithTag("migrate-manga-${eta.id}").performClick()
            waitUntil(timeoutMillis = 15_000) { navigator?.lastItem is MigrationConfigScreen }
            (navigator!!.lastItem as MigrationConfigScreen).mangaIds.toList() shouldBe listOf(eta.id)
            runOnIdle { navigator!!.pop() }

            // A long press selects, a click then toggles; invert; select all
            waitForTag("migrate-manga-${theta.id}")
            onNodeWithTag("migrate-manga-${theta.id}").performTouchInput { longClick() }
            waitForTag("migrate-manga-migrate")
            onNodeWithTag("migrate-manga-${zeta.id}").performClick()
            onNodeWithTag("migrate-manga-${theta.id}").assertIsSelected()
            onNodeWithTag("migrate-manga-${zeta.id}").assertIsSelected()
            onNodeWithTag("migrate-manga-${eta.id}").assertIsNotSelected()
            onNodeWithTag("migrate-manga-invert").performClick()
            onNodeWithTag("migrate-manga-${eta.id}").assertIsSelected()
            onNodeWithTag("migrate-manga-${theta.id}").assertIsNotSelected()
            onNodeWithTag("migrate-manga-select-all").performClick()
            onNodeWithTag("migrate-manga-${theta.id}").assertIsSelected()

            // "Migrate": the configuration for the selection, then the migration list with those entries
            onNodeWithTag("migrate-manga-migrate").performClick()
            waitUntil(timeoutMillis = 15_000) { navigator?.lastItem is MigrationConfigScreen }
            (navigator!!.lastItem as MigrationConfigScreen).mangaIds.toSet() shouldBe setOf(eta.id, theta.id, zeta.id)
            waitForTag("migration-config-continue")
            onNodeWithTag("migration-config-continue").performClick()
            val label = MR.strings.migrationConfigScreen_continueButtonText.localized()
            val sheetContinue = hasText(label) and !hasTestTag("migration-config-continue")
            waitUntil(timeoutMillis = 15_000) { onAllNodes(sheetContinue).fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(sheetContinue)[0].performClick()
            waitForTag("migration-list")
            listOf(eta, theta, zeta).forEach { waitForTag("migration-item-${it.id}") }
        }

    private companion object {
        const val ALPHA = 9_071L
        const val BETA = 9_072L

        /** A source of library entries that is not installed. */
        const val STUB = 9_079L
    }
}

/** Hosts the Migrate tab's pane like the Browse screen does. */
private object MigrateHost : Screen() {
    @Composable
    override fun Content() {
        val model = rememberScreenModel { MigrateSourceScreenModel() }
        MigrateSourcePane(model)
    }
}
