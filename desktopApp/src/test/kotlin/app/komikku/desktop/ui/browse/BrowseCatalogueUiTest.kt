package app.komikku.desktop.ui.browse

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.util.system.toast
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Headless UI test of the desktop browse flow with an in-test source: toast → snackbar, sources list
 * → catalogue (popular / latest / search) → details (description, chapters, add to library) →
 * source preferences rendered from the compat `androidx.preference` model (switch, list, edit text,
 * category + check box), values persisted in the source's preferences.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrowseCatalogueUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        // A source opens its catalogue directly here (the source feed is SourceFeedUiTest's)
        Injekt.get<UiPreferences>().useNewSourceNavigation().set(false)
        // Replace the desktop source manager (registered, not yet created) by one with the fake source
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FakeCatalogueSource())) }
    }

    private fun StringResource.text(): String = localized()

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 10_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `browse a fake source, open details, add to library, change its settings`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        setContent { KomikkuApp(context = app) }

        // Compat toasts show as snackbars
        waitForIdle()
        runOnIdle { app.toast("Hello from a toast") }
        waitForText("Hello from a toast")

        // Browse → Sources
        onNodeWithText(MR.strings.browse.text()).performClick()
        waitForText("Fake source")

        // Catalogue: popular, latest, search
        onNodeWithText("Fake source").performClick()
        waitForText("Popular manga 1")
        onNodeWithText(MR.strings.latest.text()).performClick()
        waitForText("Latest manga")
        onNode(hasSetTextAction()).performTextInput("abc")
        onNodeWithContentDescription(MR.strings.action_search.text()).performClick()
        waitForText("Result for abc")

        // Details: data from the source, add to library through UpdateManga
        onNodeWithText("Result for abc").performClick()
        waitForText("Fake description")
        waitForText("Chapter 2")
        onNodeWithText(MR.strings.add_to_library.text()).performClick()
        waitForText(MR.strings.in_library.text())
        val stored = runBlocking { Injekt.get<GetManga>().await("/manga/result-for-abc", FakeCatalogueSource.ID) }!!
        stored.favorite shouldBe true
        stored.description shouldBe "Fake description"

        // Back to the catalogue, then its settings
        onNodeWithContentDescription(MR.strings.action_bar_up_description.text()).performClick()
        waitForText("Result for abc")
        onNodeWithContentDescription(MR.strings.label_settings.text()).performClick()
        waitForText("Fake switch")

        val prefs = app.getSharedPreferences("source_${FakeCatalogueSource.ID}", Context.MODE_PRIVATE)
        onNodeWithTag("preference-fake_switch").performClick()
        waitUntil(timeoutMillis = 5_000) { prefs.getBoolean("fake_switch", false) }

        onNodeWithText("Fake list").performClick()
        waitForText("Two")
        onNodeWithText("Two").performClick()
        waitUntil(timeoutMillis = 5_000) { prefs.getString("fake_list", null) == "2" }

        onNodeWithText("Fake text").performClick()
        onNodeWithTag("preference-edit-text").performTextInput("42")
        onNodeWithText(MR.strings.action_ok.text()).performClick()
        waitUntil(timeoutMillis = 5_000) { prefs.getString("fake_text", null) == "42" }

        waitForText("Fake category")
        onNodeWithTag("preference-fake_check").performClick()
        waitUntil(timeoutMillis = 5_000) { prefs.getBoolean("fake_check", false) }
    }
}
