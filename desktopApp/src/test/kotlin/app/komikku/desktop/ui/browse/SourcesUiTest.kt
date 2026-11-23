package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import eu.kanade.domain.source.service.SourcePreferences
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.source.model.Pins
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Headless UI test of the desktop sources tab and source filter screen (P1), backed by the same `SourcePreferences` as
 * Android: only sources of enabled languages are listed, pin / unpin moves a source to the pinned group, disabling from
 * the long-press dialog hides it, and the filter screen enables a language and re-enables a single source.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourcesUiTest {

    private lateinit var app: Application
    private lateinit var preferences: SourcePreferences

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        Injekt.addSingletonFactory<SourceManager> {
            FixedSourceManager(
                listOf(
                    SimpleSearchSource(id = FIRST, name = "Alpha source"),
                    SimpleSearchSource(id = SECOND, name = "Beta source"),
                    SimpleSearchSource(id = FRENCH, name = "Source française", lang = "fr"),
                ),
            )
        }
        preferences = Injekt.get()
        // Independent of the machine's locale (whose language is enabled by default)
        preferences.enabledLanguages().set(setOf("all", "en"))
    }

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 10_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForNoText(text: String, timeoutMillis: Long = 10_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `pin, disable and filter sources and languages`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        setContent { KomikkuApp(context = app) }

        onNodeWithText(MR.strings.browse.localized()).performClick()
        waitForText("Alpha source")
        waitForText("Beta source")
        onAllNodesWithText("Source française").fetchSemanticsNodes().isEmpty() shouldBe true

        // Pin "Beta source": it moves to the pinned group
        onNode(hasContentDescription(MR.strings.action_pin.localized()) and hasAnyAncestor(hasTestTag("source-$SECOND")))
            .performClick()
        waitUntil(timeoutMillis = 5_000) { "$SECOND" in preferences.pinnedSources().get() }
        waitForText(MR.strings.pinned_sources.localized())
        onNode(hasContentDescription(MR.strings.action_unpin.localized()) and hasAnyAncestor(hasTestTag("source-$SECOND")))
            .assertExists()

        // Long press "Alpha source" → disable
        onNodeWithTag("source-$FIRST").performTouchInput { longClick() }
        waitForText(MR.strings.action_disable.localized())
        onNodeWithText(MR.strings.action_disable.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { "$FIRST" in preferences.disabledSources().get() }
        waitForNoText("Alpha source")

        // Source filter: enable French, re-enable "Alpha source"
        onNodeWithContentDescription(MR.strings.action_filter.localized()).performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Alpha source").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("language-fr").performClick()
        waitUntil(timeoutMillis = 5_000) { "fr" in preferences.enabledLanguages().get() }
        waitForText("Source française")
        onNodeWithTag("filter-source-$FIRST").performClick()
        waitUntil(timeoutMillis = 5_000) { "$FIRST" !in preferences.disabledSources().get() }

        // "All sources" switch of English disables both English sources, a second click enables them again
        onNodeWithTag("language-all-en").performClick()
        waitUntil(timeoutMillis = 5_000) { preferences.disabledSources().get() == setOf("$FIRST", "$SECOND") }
        onNodeWithTag("language-all-en").performClick()
        waitUntil(timeoutMillis = 5_000) { preferences.disabledSources().get().isEmpty() }

        // Back on the sources tab: all three sources, French under its language
        onNodeWithContentDescription(MR.strings.action_bar_up_description.localized()).performClick()
        waitForText("Alpha source")
        waitForText("Source française")

        // Unpin again
        onNode(hasContentDescription(MR.strings.action_unpin.localized()) and hasAnyAncestor(hasTestTag("source-$SECOND")))
            .performClick()
        waitUntil(timeoutMillis = 5_000) { preferences.pinnedSources().get().isEmpty() }
    }

    @Test
    fun `a failure reading the sources shows an error and a later preference change recovers`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            // A malformed SY source category entry (no "|") makes GetEnabledSources throw
            preferences.sourcesTabSourcesInCategories().set(setOf("malformed"))
            setContent { KomikkuApp(context = app) }

            onNodeWithText(MR.strings.browse.localized()).performClick()
            waitForText(KMR.strings.desktop_source_error.localized().substringBefore("%"))
            onAllNodesWithText(KMR.strings.desktop_no_sources.localized()).fetchSemanticsNodes().isEmpty() shouldBe true
            onAllNodesWithText("Alpha source").fetchSemanticsNodes().isEmpty() shouldBe true

            preferences.sourcesTabSourcesInCategories().set(emptySet())
            waitForText("Alpha source")
            waitForNoText(KMR.strings.desktop_source_error.localized().substringBefore("%"))
        }

    @Test
    fun `only an installed source becomes the last used source`() {
        val lastUsed = preferences.lastUsedSource()
        val before = lastUsed.get()
        try {
            lastUsed.set(SECOND)
            CatalogueScreenModel(sourceId = MISSING)
            runBlocking { delay(1_000) }
            lastUsed.get() shouldBe SECOND

            // Positive control: the installed source is recorded
            CatalogueScreenModel(sourceId = FIRST)
            runBlocking { withTimeout(5_000) { lastUsed.changes().first { it == FIRST } } }
        } finally {
            lastUsed.set(before)
        }
    }

    @Test
    fun `sources are grouped like on Android`() {
        fun source(id: Long, lang: String, pin: Pins = Pins.unpinned, usedLast: Boolean = false, category: String? = null) =
            Source(id, lang, "s$id", supportsLatest = false, isStub = false, pin = pin, isUsedLast = usedLast, category = category)

        val items = SourcesPaneModel.groupSources(
            listOf(
                source(1, "en"),
                source(2, "fr", pin = Pins.pinned),
                source(3, ""),
                source(4, "de"),
                source(4, "de", pin = Pins.unpinned, usedLast = true),
                source(5, "en", category = "Favourites"),
            ),
        )
        items.map {
            when (it) {
                is SourcesPaneModel.Item.Header -> "[${it.key}${if (it.isCategory) "*" else ""}]"
                is SourcesPaneModel.Item.Entry -> "${it.source.id}"
            }
        } shouldContainExactly listOf(
            "[last_used]", "4",
            "[pinned]", "2",
            "[Favourites*]", "5",
            "[de]", "4",
            "[en]", "1",
            "[]", "3",
        )
    }

    private companion object {
        const val FIRST = 7101L
        const val SECOND = 7102L
        const val FRENCH = 7103L
        const val MISSING = 7199L
    }
}
