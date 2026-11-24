package app.komikku.desktop.settings

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import app.komikku.desktop.ui.more.SettingsCategory
import app.komikku.desktop.ui.more.SettingsCategoryScreen
import app.komikku.desktop.ui.more.SettingsHomeScreen
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SettingsSearchPage
import eu.kanade.presentation.more.settings.screen.findHighlightedIndex
import eu.kanade.presentation.more.settings.screen.searchSettings
import eu.kanade.presentation.more.settings.screen.settingsBreadcrumb
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import java.io.File

/**
 * P15, headless: the desktop settings search (Android's `SettingsSearchScreen` with the shared `searchSettings`): from
 * Settings, results with their breadcrumbs across the pages, "no results", a result opening its page scrolled to the
 * highlighted item, and the shared search rules themselves.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettingsSearchUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "settings-search-ui").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
    }

    private fun ComposeUiTest.show(screen: Screen) = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme { DesktopToastHost { Navigator(screen) } }
        }
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Whether a node matching [matcher] exists and is shown (lazy list items off screen do not exist). */
    private fun ComposeUiTest.displayed(matcher: SemanticsMatcher): Boolean =
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() && onAllNodes(matcher)[0].isDisplayed()

    private fun ComposeUiTest.result(text: String) =
        displayed(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("settings-search-results")))

    private fun ComposeUiTest.onAdvanced(text: String) = displayed(hasText(text) and hasAnyAncestor(hasTestTag("settings-Advanced")))

    @Test
    fun `settings are found across the pages and a result opens its page at the item`() =
        runDesktopComposeUiTest(width = 1000, height = 700) {
            show(SettingsHomeScreen())
            waitForTag("settings-search")
            onNodeWithTag("settings-search").performClick()
            waitForTag("settings-search-query")
            // Nothing searched yet: no results list
            onAllNodesWithTag("settings-search-results").fetchSemanticsNodes().isEmpty() shouldBe true

            // A title in a group: the result with its page and group
            onNodeWithTag("settings-search-query").performTextInput("pure black")
            waitForTag("settings-search-results")
            result(MR.strings.pref_dark_theme_pure_black.localized()) shouldBe true
            val appearance = MR.strings.pref_category_appearance.localized()
            val theme = MR.strings.pref_category_theme.localized()
            result("$appearance > $theme") shouldBe true

            // The index lists the tracking screen's entries too
            onNodeWithTag("settings-search-query").performTextReplacement("AniList")
            waitUntil(timeoutMillis = 5_000) { result(MR.strings.pref_category_tracking.localized()) }

            // Nothing matches
            onNodeWithTag("settings-search-query").performTextReplacement("zzqqxx-nothing")
            waitForText(MR.strings.no_results_found.localized())

            // The last item of Advanced: its page opens scrolled to it (it is below the fold otherwise, see the other
            // test) and back leads to Settings, not to the search (the result replaced it)
            val delegated = SYMR.strings.toggle_delegated_sources.localized()
            onNodeWithTag("settings-search-query").performTextReplacement(delegated)
            waitForTag("settings-search-results")
            onAllNodes(hasText(delegated) and hasAnyAncestor(hasTestTag("settings-search-results")))[0].performClick()
            waitForTag("settings-Advanced")
            waitUntil(timeoutMillis = 10_000) { onAdvanced(delegated) }
            onNodeWithContentDescription(MR.strings.action_bar_up_description.localized()).performClick()
            waitForTag("settings-home")
            onAllNodesWithTag("settings-search-query").fetchSemanticsNodes().isEmpty() shouldBe true
        }

    @Test
    fun `without a search result the advanced page opens at its top`() = runDesktopComposeUiTest(width = 1000, height = 700) {
        show(SettingsCategoryScreen(SettingsCategory.Advanced))
        waitForTag("settings-Advanced")
        waitForIdle()
        val delegated = SYMR.strings.toggle_delegated_sources.localized()
        onAdvanced(delegated) shouldBe false
    }

    @Test
    fun `a result's highlight is used once, not again when the page is shown again`() =
        runDesktopComposeUiTest(width = 1000, height = 700) {
            val delegated = SYMR.strings.toggle_delegated_sources.localized()
            var navigator: Navigator? = null
            setContent {
                CompositionLocalProvider(LocalContext provides app) {
                    DesktopTheme {
                        DesktopToastHost {
                            Navigator(SettingsCategoryScreen(SettingsCategory.Advanced, highlightKey = delegated)) {
                                navigator = it
                                CurrentScreen()
                            }
                        }
                    }
                }
            }
            waitForTag("settings-Advanced")
            waitUntil(timeoutMillis = 10_000) { onAdvanced(delegated) }
            // Back at the top, then to another page and back: the page stays where it was left
            onNodeWithTag("settings-Advanced").performScrollToIndex(0)
            waitUntil(timeoutMillis = 5_000) { !onAdvanced(delegated) }
            runOnIdle { navigator!!.push(SettingsCategoryScreen(SettingsCategory.Appearance)) }
            waitForTag("settings-Appearance")
            runOnIdle { navigator!!.pop() }
            waitForTag("settings-Advanced")
            // Longer than the highlight's delay and scroll
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            onAdvanced(delegated) shouldBe false
        }

    @Test
    fun `the highlighted index skips disabled groups like the rendered lists`() {
        fun text(title: String) = Preference.PreferenceItem.TextPreference(title = title)
        val contents = listOf(
            Preference.PreferenceGroup(title = "Off", enabled = false, preferenceItems = persistentListOf(text("A"), text("B"))),
            Preference.PreferenceGroup(title = "On", preferenceItems = persistentListOf(text("C"))),
            text("D"),
        )
        // PreferenceList / Android's PreferenceScreen render: header "On", C, spacer, D
        contents.findHighlightedIndex("C") shouldBe 1
        contents.findHighlightedIndex("D") shouldBe 3
        contents.findHighlightedIndex("A") shouldBe -1
    }

    @Test
    fun `the shared search follows Android's rules`() {
        fun text(title: String, subtitle: String? = null, enabled: Boolean = true) =
            Preference.PreferenceItem.TextPreference(title = title, subtitle = subtitle, enabled = enabled)
        val pages = listOf(
            SettingsSearchPage(
                title = "Page A",
                route = "a",
                contents = listOf(
                    text("Alpha"),
                    text("Hidden alpha", enabled = false),
                    Preference.PreferenceItem.InfoPreference("Alpha info"),
                    Preference.PreferenceGroup(title = "Group", preferenceItems = persistentListOf(text("Beta", subtitle = "about alpha"))),
                    Preference.PreferenceGroup(title = "Off", enabled = false, preferenceItems = persistentListOf(text("Alpha off"))),
                ),
            ),
            SettingsSearchPage(title = "Page B", route = "b", contents = (1..12).map { text("Alpha $it") }),
        )
        // Enabled items only (of enabled groups), subtitles too, no info items, case ignored, at most ten
        val results = searchSettings(pages, "ALPHA", isLtr = true)
        results.map { it.title } shouldBe listOf("Alpha", "Beta") + (1..8).map { "Alpha $it" }
        results[1].breadcrumbs shouldBe "Page A > Group"
        results[1].route shouldBe "a"
        results[1].highlightKey shouldBe "Beta"
        searchSettings(pages, "", isLtr = true) shouldBe emptyList()
        settingsBreadcrumb("Page", "Group", isLtr = false) shouldBe "Group < Page"
        settingsBreadcrumb("Page", null, isLtr = false) shouldBe "Page"
        // A group's header and spacer count as list items (Android's PreferenceScreen)
        pages[0].contents.findHighlightedIndex("Beta") shouldBe 4
    }
}
