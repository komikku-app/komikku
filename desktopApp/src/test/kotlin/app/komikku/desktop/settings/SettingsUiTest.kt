package app.komikku.desktop.settings

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.reader.ReaderLauncher
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import exh.pref.DelegateSourcePreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * 8.4 UI, headless: More toggles, settings screens bound to the real preference classes (borrowed
 * settings model + widgets) and category management.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettingsUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "settings-ui").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }

    private fun ComposeUiTest.waitForText(text: String, present: Boolean = true) {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() == present }
    }

    private fun ComposeUiTest.back() = onNodeWithContentDescription(MR.strings.action_bar_up_description.localized()).performClick()

    private fun categories() = runBlocking { Injekt.get<GetCategories>().await().filterNot { it.isSystemCategory } }

    @Test
    fun `more toggles, settings and categories`() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        Injekt.get<ReaderLauncher>().close()
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.label_more.localized()).performClick()
        waitForTag("more-incognito")

        // Incognito toggle (More, like Android)
        onNodeWithTag("more-incognito").performClick()
        waitUntil(timeoutMillis = 5_000) { Injekt.get<BasePreferences>().incognitoMode().get() }

        // Settings → Appearance: theme mode list dialog, pure black switch
        onNodeWithTag("more-settings").performClick()
        waitForTag("settings-appearance")
        onNodeWithTag("settings-appearance").performClick()
        waitForTag("settings-Appearance")
        onAllNodesWithText(MR.strings.theme_system.localized())[0].performClick()
        waitForText(MR.strings.theme_dark.localized())
        onNodeWithText(MR.strings.theme_dark.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { Injekt.get<UiPreferences>().themeMode().get() == ThemeMode.DARK }
        onNodeWithText(MR.strings.pref_dark_theme_pure_black.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { Injekt.get<UiPreferences>().themeDarkAmoled().get() }
        back()

        // Reader: default reading mode
        waitForTag("settings-reader")
        onNodeWithTag("settings-reader").performClick()
        waitForTag("settings-Reader")
        onNodeWithText(MR.strings.pref_viewer_type.localized()).performClick()
        waitForText(ReadingMode.WEBTOON.stringRes.localized())
        onNodeWithText(ReadingMode.WEBTOON.stringRes.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { Injekt.get<ReaderPreferences>().defaultReadingMode().get() == ReadingMode.WEBTOON.flagValue }
        back()

        // Downloads: CBZ off
        waitForTag("settings-downloads")
        onNodeWithTag("settings-downloads").performClick()
        waitForTag("settings-Downloads")
        Injekt.get<DownloadPreferences>().saveChaptersAsCBZ().get() shouldBe true
        onNodeWithText(MR.strings.save_chapter_as_cbz.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { !Injekt.get<DownloadPreferences>().saveChaptersAsCBZ().get() }
        back()

        // Browse → Feed: Android's "feed tab in front" and "hide feed tab" (P13 review fixes)
        waitForTag("settings-browse")
        onNodeWithTag("settings-browse").performClick()
        waitForTag("settings-Browse")
        val feedTabInFront = Injekt.get<UiPreferences>().feedTabInFront()
        onNodeWithTag("settings-Browse").performScrollToNode(hasText(SYMR.strings.pref_feed_position.localized()))
        onNodeWithText(SYMR.strings.pref_feed_position.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { feedTabInFront.get() }
        feedTabInFront.set(false)
        val hideFeedTab = Injekt.get<UiPreferences>().hideFeedTab()
        hideFeedTab.get() shouldBe false
        onNodeWithTag("settings-Browse").performScrollToNode(hasText(SYMR.strings.pref_hide_feed.localized()))
        onNodeWithText(SYMR.strings.pref_hide_feed.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { hideFeedTab.get() }
        hideFeedTab.set(false)
        back()

        // Advanced → Developer tools: SY's delegated sources toggle (P4; there like on Android since P12), on by default
        waitForTag("settings-advanced")
        onNodeWithTag("settings-advanced").performClick()
        waitForTag("settings-Advanced")
        val delegates = Injekt.get<DelegateSourcePreferences>().delegateSources()
        delegates.get() shouldBe true
        onNodeWithTag("settings-Advanced").performScrollToNode(hasText(SYMR.strings.toggle_delegated_sources.localized()))
        onNodeWithText(SYMR.strings.toggle_delegated_sources.localized()).performClick()
        waitUntil(timeoutMillis = 5_000) { !delegates.get() }
        delegates.set(true)
        back()

        // About shows the version
        waitForTag("settings-about")
        onNodeWithTag("settings-about").performClick()
        waitForTag("settings-About")
        waitForText(MR.strings.app_name.localized())
        back()
        waitForTag("settings-home")
        back()

        // Categories: add two, rename, move, hide, delete
        waitForTag("more-categories")
        onNodeWithTag("more-categories").performClick()
        listOf("Action", "Comedy").forEach { name ->
            waitForTag("category-add")
            onNodeWithTag("category-add").performClick()
            waitForTag("category-name")
            onNodeWithTag("category-name").performTextReplacement(name)
            onNodeWithTag("category-name-confirm").performClick()
            waitForTag("category-$name")
        }
        categories().map { it.name } shouldBe listOf("Action", "Comedy")

        onNodeWithTag("category-rename-Comedy").performClick()
        waitForTag("category-name")
        onNodeWithTag("category-name").performTextReplacement("Action")
        waitForText(MR.strings.error_category_exists.localized())
        onNodeWithTag("category-name").performTextReplacement("Drama")
        onNodeWithTag("category-name-confirm").performClick()
        waitForTag("category-Drama")

        onNodeWithTag("category-up-Drama").performClick()
        waitUntil(timeoutMillis = 5_000) { categories().sortedBy { it.order }.map { it.name } == listOf("Drama", "Action") }

        onNodeWithTag("category-hide-Action").performClick()
        waitUntil(timeoutMillis = 5_000) { categories().single { it.name == "Action" }.hidden }

        onNodeWithTag("category-delete-Action").performClick()
        onNodeWithTag("category-delete-confirm").performClick()
        waitForTag("category-Action", present = false)
        categories().map { it.name } shouldBe listOf("Drama")
        Unit
    }
}
