package app.komikku.desktop.security

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.reader.ReaderLauncher
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P16, headless: the desktop app lock from Settings → Security and Privacy (set, lock now, unlock, turn off with the
 * current secret) and at start-up (only the lock screen until unlocked).
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppLockUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
    }

    @BeforeEach
    fun reset() {
        Injekt.get<SecurityPreferences>().useAuthenticator().delete()
        Injekt.get<PreferenceStore>().getString(Preference.appStateKey(DesktopAppLock.SECRET_KEY), "").delete()
        newStart()
    }

    private lateinit var appLock: DesktopAppLock

    /**
     * A new app start: a new lock (the app lock is created once per process). Through a plain factory returning it:
     * Injekt keeps the first instance of a singleton (`addSingleton` / `addSingletonFactory` do not replace it).
     */
    private fun newStart() {
        appLock = DesktopAppLock(Injekt.get(), Injekt.get())
        Injekt.addFactory { appLock }
    }

    private fun lock() = Injekt.get<DesktopAppLock>()

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }

    private fun ComposeUiTest.waitForText(text: String) {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.openSecuritySettings() {
        Injekt.get<ReaderLauncher>().close()
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.label_more.localized()).performClick()
        waitForTag("more-settings")
        onNodeWithTag("more-settings").performClick()
        waitForTag("settings-security")
        onNodeWithTag("settings-security").performClick()
        waitForTag("settings-Security")
    }

    @Test
    fun `the lock is set in the settings, locks now, unlocks and is turned off with the current secret`() =
        runDesktopComposeUiTest(width = 1100, height = 900) {
            openSecuritySettings()
            onNodeWithText(KMR.strings.desktop_app_lock.localized()).performClick()
            waitForTag("app-lock-dialog")
            // Too short, then two different entries
            onNodeWithTag("app-lock-new").performTextInput("12")
            onNodeWithTag("app-lock-repeat").performTextInput("12")
            onNodeWithTag("app-lock-confirm").performClick()
            waitForText(KMR.strings.desktop_app_lock_too_short.localized().substringBefore("%"))
            onNodeWithTag("app-lock-new").performTextReplacement("1234")
            onNodeWithTag("app-lock-repeat").performTextReplacement("1235")
            onNodeWithTag("app-lock-confirm").performClick()
            waitForText(KMR.strings.desktop_app_lock_mismatch.localized())
            onNodeWithTag("app-lock-repeat").performTextReplacement("1234")
            onNodeWithTag("app-lock-confirm").performClick()
            waitForTag("app-lock-dialog", present = false)
            waitUntil(timeoutMillis = 10_000) { lock().enabled.value }

            // Lock now: only the lock screen is there (the settings behind it are hidden from the semantics)
            onNodeWithText(KMR.strings.desktop_app_lock_now.localized()).performClick()
            waitForTag("app-lock")
            onAllNodesWithTag("settings-Security").fetchSemanticsNodes().isEmpty() shouldBe true
            onNodeWithTag("app-lock-input").performTextInput("9999")
            onNodeWithTag("app-lock-unlock").performClick()
            waitForText(KMR.strings.desktop_app_lock_wrong.localized())
            lock().locked.value shouldBe true
            onNodeWithTag("app-lock-input").performTextInput("1234")
            onNodeWithTag("app-lock-unlock").performClick()
            waitForTag("app-lock", present = false)
            // The page under the lock kept its state
            waitForTag("settings-Security")

            // Turning it off asks for the current secret
            onNodeWithText(KMR.strings.desktop_app_lock.localized()).performClick()
            waitForTag("app-lock-current")
            onNodeWithTag("app-lock-current").performTextInput("0000")
            onNodeWithTag("app-lock-confirm").performClick()
            waitForText(KMR.strings.desktop_app_lock_wrong.localized())
            lock().enabled.value shouldBe true
            onNodeWithTag("app-lock-current").performTextReplacement("1234")
            onNodeWithTag("app-lock-confirm").performClick()
            waitUntil(timeoutMillis = 10_000) { !lock().enabled.value }
            Injekt.get<SecurityPreferences>().useAuthenticator().get() shouldBe false
        }

    @Test
    fun `a locked start shows only the lock screen until unlocked`() = runDesktopComposeUiTest(width = 1100, height = 900) {
        runBlocking { lock().enable("secret") }
        newStart()
        lock().locked.value shouldBe true
        Injekt.get<ReaderLauncher>().close()
        setContent { KomikkuApp(context = app) }
        waitForTag("app-lock")
        onAllNodesWithText(MR.strings.label_more.localized()).fetchSemanticsNodes().isEmpty() shouldBe true
        onNodeWithTag("app-lock-input").performTextInput("secret")
        onNodeWithTag("app-lock-unlock").performClick()
        waitForTag("app-lock", present = false)
        waitForText(MR.strings.label_more.localized())
    }
}
