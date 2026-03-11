package tachiyomi.macrobenchmark

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import eu.kanade.tachiyomi.BuildConfig
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val APP_PACKAGE = BuildConfig.APPLICATION_ID
private const val LAUNCH_TIMEOUT_MS = 10_000L
private const val UI_TIMEOUT_MS = 5_000L

/**
 * Instrumented tests for bottom-navigation tab switching using UiAutomator.
 *
 * Assumes onboarding has already been completed (or calls [initAppData] first).
 * Each test locates a tab by its content description and clicks it, then
 * asserts the tab is still present (i.e. the screen did not crash).
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        device.pressHome()
        device.waitForIdle()

        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = context.packageManager
            .getLaunchIntentForPackage(APP_PACKAGE)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        checkNotNull(intent) { "Could not resolve launch intent for $APP_PACKAGE" }
        context.startActivity(intent)

        device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT_MS)

        // Complete onboarding if this is a fresh install.
        initAppData(device, UI_TIMEOUT_MS)
    }

    /** Taps the Library tab and verifies it is displayed. */
    @Test
    fun tabNavigation_library_isReachable() {
        val tab = device.wait(Until.findObject(By.desc("Library")), UI_TIMEOUT_MS)
        assertNotNull("Library tab should be visible", tab)
        tab.click()
        device.waitForIdle()
    }

    /** Taps the Updates tab and verifies it is displayed. */
    @Test
    fun tabNavigation_updates_isReachable() {
        val tab = device.wait(Until.findObject(By.desc("Updates")), UI_TIMEOUT_MS)
        assertNotNull("Updates tab should be visible", tab)
        tab.click()
        device.waitForIdle()
        assertNotNull(
            "Updates tab should still be visible after tap",
            device.findObject(By.desc("Updates")),
        )
    }

    /** Taps the History tab and verifies it is displayed. */
    @Test
    fun tabNavigation_history_isReachable() {
        val tab = device.wait(Until.findObject(By.desc("History")), UI_TIMEOUT_MS)
        assertNotNull("History tab should be visible", tab)
        tab.click()
        device.waitForIdle()
        assertNotNull(
            "History tab should still be visible after tap",
            device.findObject(By.desc("History")),
        )
    }

    /** Taps the Browse tab and verifies it is displayed. */
    @Test
    fun tabNavigation_browse_isReachable() {
        val tab = device.wait(Until.findObject(By.desc("Browse")), UI_TIMEOUT_MS)
        assertNotNull("Browse tab should be visible", tab)
        tab.click()
        device.waitForIdle()
        assertNotNull(
            "Browse tab should still be visible after tap",
            device.findObject(By.desc("Browse")),
        )
    }

    /** Taps the More tab and verifies it is displayed. */
    @Test
    fun tabNavigation_more_isReachable() {
        val tab = device.wait(Until.findObject(By.desc("More")), UI_TIMEOUT_MS)
        assertNotNull("More tab should be visible", tab)
        tab.click()
        device.waitForIdle()
        assertNotNull(
            "More tab should still be visible after tap",
            device.findObject(By.desc("More")),
        )
    }
}
