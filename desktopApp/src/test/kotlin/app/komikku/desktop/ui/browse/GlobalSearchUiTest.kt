package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.mihon.MangayomiJsRuntime
import app.komikku.sourcejs.mihon.MangayomiJsSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Headless UI test of the desktop global search (P1): the borrowed Android screen model searches every enabled source
 * (a Mihon-style source, a JavaScript source, one without results, one that fails; a source of a disabled language and
 * a disabled source are left out), rows show results, "no results" and the error; "has results" hides the others,
 * "pinned" searches only pinned sources; a row opens that source's own search with the query, a result opens the
 * details, and the details title starts a new global search.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobalSearchUiTest {

    private lateinit var app: Application
    private lateinit var runtime: MangayomiJsRuntime
    private lateinit var jsSource: MangayomiJsSource
    private lateinit var preferences: SourcePreferences

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        runtime = MangayomiJsRuntime(app, Injekt.get<NetworkHelper>())
        jsSource = runBlocking {
            runtime.source(
                JsSourceDescriptor(
                    id = 4343L,
                    name = "JS Search Source",
                    lang = "en",
                    baseUrl = "https://js.example.org",
                    sourceCodeUrl = "https://raw.example.org/js-search.js",
                ),
                JS_CODE,
            )
        }
        Injekt.addSingletonFactory<SourceManager> {
            FixedSourceManager(
                listOf(
                    FakeCatalogueSource(),
                    jsSource,
                    SimpleSearchSource(id = EMPTY, name = "Empty source", prefix = null),
                    SimpleSearchSource(id = FAILING, name = "Failing source", error = "Boom from the source"),
                    SimpleSearchSource(id = FRENCH, name = "Source française", lang = "fr"),
                    SimpleSearchSource(id = DISABLED, name = "Disabled source"),
                ),
            )
        }
        preferences = Injekt.get()
        preferences.enabledLanguages().set(setOf("all", "en"))
        preferences.disabledSources().set(setOf("$DISABLED"))
    }

    @AfterAll
    fun stop() {
        if (::runtime.isInitialized) runtime.close()
    }

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 20_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForNoText(text: String, timeoutMillis: Long = 10_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }
    }

    private fun ComposeUiTest.hasNoText(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty()

    @Test
    fun `search all enabled sources, filter, open a source and a result`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        setContent { KomikkuApp(context = app) }

        onNodeWithText(MR.strings.browse.localized()).performClick()
        waitForText("Fake source")
        onNodeWithContentDescription(MR.strings.action_global_search.localized()).performClick()
        waitForText(MR.strings.has_results.localized())
        // No pinned source: Android switches to all sources and hides the pinned chip
        waitUntil(timeoutMillis = 5_000) { preferences.globalSearchPinnedState().get() == SourceFilter.All }
        hasNoText(MR.strings.pinned_sources.localized()) shouldBe true

        onNode(hasSetTextAction()).performTextInput("abc")
        onNodeWithContentDescription(MR.strings.action_search.localized()).performClick()
        waitForText("Result for abc")
        waitForText("JS hit abc")
        waitForText("Boom from the source")
        onNode(hasText(MR.strings.no_results_found.localized()) and hasAnyAncestor(hasTestTag("global-search-source-$EMPTY")))
            .assertExists()
        hasNoText("Source française") shouldBe true
        hasNoText("Disabled source") shouldBe true

        // Only rows with results
        onNodeWithText(MR.strings.has_results.localized()).performClick()
        waitForNoText("Failing source")
        waitForNoText("Empty source")
        waitForText("JS hit abc")
        preferences.globalSearchFilterState().get() shouldBe true
        onNodeWithText(MR.strings.has_results.localized()).performClick()
        waitForText("Failing source")

        // A row header opens the source's own search with the query
        onNodeWithText("Fake source").performClick()
        waitForText(MR.strings.popular.localized())
        waitForText("Result for abc")
        onNode(hasSetTextAction()).assert(hasText("abc"))
        onNodeWithContentDescription(MR.strings.action_bar_up_description.localized()).performClick()
        waitForText("JS hit abc")

        // A result opens the details; the details title searches it globally
        onNodeWithText("Result for abc").performClick()
        waitForText("Fake description")
        onNodeWithTag("details-title").performClick()
        waitForText("Result for Result for abc")
        waitForText("JS hit Result for abc")
    }

    @Test
    fun `only pinned sources are searched with the pinned filter`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        preferences.pinnedSources().set(setOf("${jsSource.id}"))
        setContent { KomikkuApp(context = app) }

        onNodeWithText(MR.strings.browse.localized()).performClick()
        waitForText("Fake source")
        onNodeWithContentDescription(MR.strings.action_global_search.localized()).performClick()
        waitForText(MR.strings.pinned_sources.localized())
        onNodeWithText(MR.strings.pinned_sources.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { preferences.globalSearchPinnedState().get() == SourceFilter.PinnedOnly }

        onNode(hasSetTextAction()).performTextInput("xyz")
        onNodeWithContentDescription(MR.strings.action_search.localized()).performClick()
        waitForText("JS hit xyz")
        hasNoText("Result for xyz") shouldBe true
        hasNoText("Failing source") shouldBe true

        // All sources again, with the same query: the other sources are searched too
        onNodeWithText(MR.strings.all.localized()).performClick()
        waitForText("Result for xyz")
        waitForText("Boom from the source")
        preferences.pinnedSources().set(emptySet())
    }

    private companion object {
        const val EMPTY = 7201L
        const val FAILING = 7202L
        const val FRENCH = 7203L
        const val DISABLED = 7204L

        /** A Mangayomi-style extension whose search needs no network. */
        val JS_CODE = """
            const mangayomiSources = [{ "name": "JS Search Source", "lang": "en", "id": 4343 }];
            class DefaultExtension extends MProvider {
              async getPopular(page) { return { list: [], hasNextPage: false }; }
              async search(query, page, filters) {
                return { list: [{ name: "JS hit " + query, link: "/js/" + encodeURIComponent(query) }], hasNextPage: false };
              }
              async getDetail(url) { return { name: url, chapters: [] }; }
              async getPageList(url) { return []; }
            }
        """.trimIndent()
    }
}
