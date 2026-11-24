package app.komikku.desktop.security

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
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
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.desktop.ui.security.AppLockGate
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
        Injekt.get<PreferenceStore>().getInt(Preference.appStateKey(DesktopAppLock.FAILURES_KEY), 0).delete()
        Injekt.get<PreferenceStore>().getLong(Preference.appStateKey(DesktopAppLock.BLOCKED_UNTIL_KEY), 0L).delete()
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
            Injekt.get<PreferenceStore>().getString(Preference.appStateKey(DesktopAppLock.SECRET_KEY), "").isSet() shouldBe false
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

    @Test
    fun `dialogs and toasts of the app are neither shown nor usable while locked`() =
        runDesktopComposeUiTest(width = 1100, height = 900) {
            openSecuritySettings()
            runBlocking { lock().enable("1234") }
            // A dialog of the app is open when the lock comes (e.g. "Lock when idle" after the window was minimised)
            onNodeWithText(KMR.strings.desktop_app_lock_change.localized()).performClick()
            waitForTag("app-lock-dialog")
            onNodeWithTag("app-lock-current").performTextInput("12")
            runOnIdle { lock().lockNow() }
            waitForTag("app-lock")
            waitForIdle()
            // Neither the dialog nor its text are there, and a toast shown now does not appear over the lock screen
            onAllNodesWithTag("app-lock-dialog").fetchSemanticsNodes().isEmpty() shouldBe true
            onAllNodesWithTag("app-lock-current").fetchSemanticsNodes().isEmpty() shouldBe true
            Toast.makeText(app, "A toast while locked", Toast.LENGTH_LONG).show()
            waitForIdle()
            onAllNodesWithText("A toast while locked").fetchSemanticsNodes().isEmpty() shouldBe true
            // The lock screen gets the input (the dialog does not take it, and is not dismissed by it)
            onNodeWithTag("app-lock-input").performTextInput("1234")
            onNodeWithTag("app-lock-unlock").performClick()
            waitForTag("app-lock", present = false)
            // The dialog is back as it was
            waitForTag("app-lock-dialog")
            onNodeWithTag("app-lock-current").assertTextContains("••", substring = true)
        }

    @Test
    fun `menus and dialogs inside the gate are hidden while locked and come back after unlocking`() =
        runDesktopComposeUiTest(width = 900, height = 700) {
            runBlocking { lock().enable("secret") }
            newStart()
            var dismissed = 0
            setContent {
                CompositionLocalProvider(LocalContext provides app) {
                    DesktopTheme {
                        AppLockGate(lock()) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text("The page")
                                DropdownMenu(expanded = true, onDismissRequest = { dismissed++ }) {
                                    DropdownMenuItem(text = { Text("A menu entry") }, onClick = {})
                                }
                            }
                            AlertDialog(
                                onDismissRequest = { dismissed++ },
                                confirmButton = { TextButton(onClick = {}) { Text("A dialog button") } },
                                text = { Text("A dialog text") },
                            )
                        }
                    }
                }
            }
            waitForTag("app-lock")
            waitForIdle()
            listOf("The page", "A menu entry", "A dialog text", "A dialog button").forEach {
                onAllNodesWithText(it).fetchSemanticsNodes().isEmpty() shouldBe true
            }
            onNodeWithTag("app-lock-input").performTextInput("secret")
            onNodeWithTag("app-lock-unlock").performClick()
            waitForTag("app-lock", present = false)
            listOf("The page", "A menu entry", "A dialog text", "A dialog button").forEach { waitForText(it) }
            // Clicks and keys on the lock screen never reached the menu or the dialog
            dismissed shouldBe 0
        }
}
