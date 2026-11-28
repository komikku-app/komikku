package app.komikku.desktop.updater

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import tachiyomi.data.release.GitHubAsset
import tachiyomi.data.release.GithubRelease
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
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

    @Test
    fun `when the browser cannot be opened the dialog stays and shows the release page address`() = runDesktopComposeUiTest {
        fake.failure = null
        fake.releases = newer
        runBlocking { checker.check(forceCheck = true) }
        var attempts = 0
        setContent {
            AppUpdateDialogHost(checker = checker, openUrl = {
                attempts++
                false
            })
        }
        waitForTag("app-update-dialog")
        onAllNodesWithTag("app-update-release-link").fetchSemanticsNodes().isEmpty() shouldBe true
        onNodeWithTag("app-update-open").performClick()
        waitForTag("app-update-release-link")
        onNodeWithTag("app-update-release-link").assertTextEquals("https://github.com/komikku-app/komikku/releases/tag/v1.14.0")
        onNodeWithText(KMR.strings.desktop_browser_open_failed.localized()).assertExists()
        onNodeWithTag("app-update-dialog").assertExists()
        attempts shouldBe 1
        checker.available.value?.version shouldBe "v1.14.0"

        // "Not now" still closes it
        onNodeWithTag("app-update-dismiss").performClick()
        waitForTag("app-update-dialog", present = false)
    }

    @Test
    fun `an offered RPM shows whether it is signed, its install commands and a link to the instructions`() = runDesktopComposeUiTest(
        width = 1000,
        height = 900,
    ) {
        val opened = mutableListOf<String>()
        var release by mutableStateOf(rpmRelease)
        var assetNames by mutableStateOf(listOf("Komikku-1.14.0-linux-x64.rpm", "Komikku-1.14.0-linux-x64.deb"))
        setContent {
            AppUpdateDialog(release = release, onOpen = {}, onDismiss = {}, assetNames = assetNames, openUrl = {
                opened += it
                false
            })
        }
        waitForTag("app-update-rpm")
        onNodeWithText(KMR.strings.desktop_update_rpm_unsigned.localized()).assertExists()
        onNodeWithTag("app-update-rpm-commands").assertTextEquals(
            "sudo dnf install ./Komikku-1.14.0-linux-x64.rpm\nsudo rpm -i --nosignature ./Komikku-1.14.0-linux-x64.rpm",
        )
        // The release notes' Linux section; the address stays on screen when the browser cannot open it
        onNodeWithTag("app-update-rpm-instructions").performClick()
        waitForTag("app-update-rpm-instructions-link")
        opened shouldBe listOf("https://github.com/komikku-app/komikku/releases/tag/v1.14.0#linux")
        onNodeWithTag("app-update-rpm-instructions-link")
            .assertTextEquals("https://github.com/komikku-app/komikku/releases/tag/v1.14.0#linux")

        assetNames = assetNames + "Komikku-1.14.0-linux-x64-rpm-signing-key.asc"
        waitForText(KMR.strings.desktop_update_rpm_signed.localized())
        onAllNodesWithText(KMR.strings.desktop_update_rpm_unsigned.localized()).fetchSemanticsNodes().isEmpty() shouldBe true
        onNodeWithTag("app-update-rpm-commands").assertTextEquals(
            "sudo rpm --import ./Komikku-1.14.0-linux-x64-rpm-signing-key.asc\nsudo dnf install ./Komikku-1.14.0-linux-x64.rpm",
        )

        release = newer.first()
        waitForTag("app-update-rpm", present = false)
        onNodeWithTag("app-update-dialog").assertExists()
    }

    @Test
    fun `the update found by the check shows its release's signing key`() = runDesktopComposeUiTest(width = 1000, height = 900) {
        fake.failure = null
        // What the ReleaseService's download link selection records for the release it returns
        val github = GithubRelease(
            version = "v1.14.0",
            info = "Faster reader",
            releaseLink = "https://github.com/komikku-app/komikku/releases/tag/v1.14.0",
            assets = listOf("Komikku-1.14.0-linux-x64.rpm", "Komikku-1.14.0-linux-x64-rpm-signing-key.asc")
                .map { GitHubAsset(it, "https://github.com/komikku-app/komikku/releases/download/v1.14.0/$it") },
        )
        val rpmConfig = DesktopUpdaterConfig(
            enabled = true,
            isPreview = false,
            versionName = "1.13.6",
            commitCount = 0,
            os = DesktopOs.Linux,
            arch = DesktopArch.X64,
            linuxPackaging = LinuxPackaging.Rpm,
        )
        val link = checker.releaseAssets.selector(rpmConfig)(github)
        link shouldBe rpmRelease.downloadLink
        fake.releases = listOf(rpmRelease)
        runBlocking { checker.check(forceCheck = true) }
        setContent { AppUpdateDialogHost(checker = checker, openUrl = { true }) }
        waitForText(KMR.strings.desktop_update_rpm_signed.localized())
        checker.dismiss()
    }

    private val rpmRelease = Release(
        version = "v1.14.0",
        info = "Faster reader",
        releaseLink = "https://github.com/komikku-app/komikku/releases/tag/v1.14.0",
        downloadLink = "https://github.com/komikku-app/komikku/releases/download/v1.14.0/Komikku-1.14.0-linux-x64.rpm",
    )

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
