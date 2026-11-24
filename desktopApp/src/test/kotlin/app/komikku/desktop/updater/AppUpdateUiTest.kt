package app.komikku.desktop.updater

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException

/**
 * P6: Settings → About with the updater enabled: "Check for updates" (forced), the update dialog with the release notes
 * and "Open on GitHub", "No new updates available" and a failure message as toasts. The app's checker is replaced by
 * one on a fake release source (the build under test has the updater off).
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppUpdateUiTest {

    private lateinit var app: Application

    private class FakeReleases : ReleaseService {
        var releases: List<Release> = emptyList()
        var failure: Exception? = null
        override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? = error("not used")
        override suspend fun releaseNotes(arguments: GetApplicationRelease.Arguments): List<Release> {
            failure?.let { throw it }
            return releases
        }
    }

    private val fake = FakeReleases()
    private lateinit var checker: DesktopAppUpdateChecker

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "app-update-ui").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
        // The default build has the updater off: no "Check for updates"
        Injekt.get<DesktopUpdaterConfig>().isActive shouldBe false
        val config = DesktopUpdaterConfig(enabled = true, isPreview = false, versionName = "1.13.6", commitCount = 0, os = DesktopOs.Linux)
        checker = DesktopAppUpdateChecker(GetApplicationRelease(fake, Injekt.get()), config, Injekt.get())
        Injekt.addSingleton(checker)
    }

    @Test
    fun `check for updates from the about screen`() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.label_more.localized()).performClick()
        waitForTag("more-settings")
        onNodeWithTag("more-settings").performClick()
        waitForTag("settings-about")
        onNodeWithTag("settings-about").performClick()
        waitForText(MR.strings.version.localized())

        // Nothing newer: Android's toast
        fake.releases = listOf(Release("v1.13.6", "Old notes", "https://github.com/komikku-app/komikku/releases/tag/v1.13.6", "x.deb"))
        onNodeWithText(MR.strings.check_for_updates.localized()).performClick()
        waitForText(MR.strings.update_check_no_new_updates.localized())

        // Network failure: the error's message
        fake.failure = IOException("GitHub unreachable")
        onNodeWithText(MR.strings.check_for_updates.localized()).performClick()
        waitForText("GitHub unreachable")
        fake.failure = null

        // A newer release: the window's update dialog with the notes; "Not now" closes it
        fake.releases = newer
        onNodeWithText(MR.strings.check_for_updates.localized()).performClick()
        waitForTag("app-update-dialog")
        onNodeWithText(MR.strings.update_check_notification_update_available.localized()).assertExists()
        onNodeWithText("Latest: v1.14.0").assertExists()
        // The notes as plain text, the interactor's `\r` separators as line breaks
        onNodeWithTag("app-update-notes").assertTextEquals("## v1.14.0\n\nFaster reader")
        onNodeWithTag("app-update-dismiss").performClick()
        waitForTag("app-update-dialog", present = false)
        checker.available.value.shouldBeNull()
    }

    @Test
    fun `open on GitHub opens the release page in the browser`() = runDesktopComposeUiTest {
        fake.failure = null
        fake.releases = newer
        runBlocking { checker.check(forceCheck = true) }
        val opened = mutableListOf<String>()
        setContent {
            AppUpdateDialogHost(checker = checker, openUrl = {
                opened += it
                true
            })
        }
        waitForTag("app-update-dialog")
        onNodeWithText(MR.strings.update_check_open.localized()).assertExists()
        onNodeWithTag("app-update-open").performClick()
        waitForTag("app-update-dialog", present = false)
        opened shouldBe listOf("https://github.com/komikku-app/komikku/releases/tag/v1.14.0")
        checker.available.value.shouldBeNull()
    }

    private val newer = listOf(
        Release("v1.14.0", "Faster reader", "https://github.com/komikku-app/komikku/releases/tag/v1.14.0", "x.deb"),
        Release("v1.13.6", "Old notes", "https://github.com/komikku-app/komikku/releases/tag/v1.13.6", "x.deb"),
    )

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true, timeoutMillis: Long = 15_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }
}
