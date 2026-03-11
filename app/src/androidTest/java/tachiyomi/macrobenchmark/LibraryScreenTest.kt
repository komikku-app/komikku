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
 * Instrumented tests for the Library screen using UiAutomator.
 *
 * Navigates to the Library tab and verifies common UI affordances.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

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

    private fun openLibrary() {
        val tab = device.wait(Until.findObject(By.text("Library")), UI_TIMEOUT_MS)
        assertNotNull("Library tab must be reachable", tab)
        tab.click()
        device.waitForIdle()
    }

    /**
     * Verifies the Library screen is displayed after tapping the Library tab.
     */
    @Test
    fun libraryScreen_isDisplayedAfterTabTap() {
        openLibrary()
        assertNotNull(
            "App window should still be present after opening Library",
            device.findObject(By.pkg(APP_PACKAGE).depth(0)),
        )
    }

    /**
     * Verifies the search icon is visible on the Library screen.
     */
    @Test
    fun libraryScreen_searchIconIsVisible() {
        openLibrary()
        val searchBtn = device.wait(Until.findObject(By.descContains("Search")), UI_TIMEOUT_MS)
        assertNotNull("Search button should be visible on the Library screen", searchBtn)
    }

    /**
     * Taps the search button and verifies the app does not crash.
     */
    @Test
    fun libraryScreen_searchTap_doesNotCrash() {
        openLibrary()
        val searchBtn = device.wait(Until.findObject(By.descContains("Search")), UI_TIMEOUT_MS)
        searchBtn?.click()
        device.waitForIdle()
        assertNotNull(
            "App window should still be present after tapping Search",
            device.findObject(By.pkg(APP_PACKAGE).depth(0)),
        )
    }

    /**
     * Taps the filter icon (if present) and verifies the app does not crash.
     */
    @Test
    fun libraryScreen_filterTap_doesNotCrash() {
        openLibrary()
        // Filter button may or may not be present depending on library state.
        device.findObject(By.descContains("Filter"))?.click()
        device.waitForIdle()
        assertNotNull(
            "App window should still be present after tapping Filter",
            device.findObject(By.pkg(APP_PACKAGE).depth(0)),
        )
    }

    @Test
    fun performVariousActions_doesNotCrash() {
        Thread.sleep(10000)
        device.waitForIdle()

        device.findObject(By.descContains("Filter"))?.click()
        device.waitForIdle()
        Thread.sleep(1000)
        device.findObject(By.text("Downloaded"))?.let {
            it.click()
            device.waitForIdle()
            it.click()
            device.waitForIdle()
            it.click()
            device.waitForIdle()
        }
        device.pressBack()
        device.waitForIdle()

        device.findObject(By.desc("More options"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Update category"))?.click()
        device.waitForIdle()

        // Open library manga
        device.click(200, 600)
        Thread.sleep(5000)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        device.findObject(By.text("Updates"))?.click()
        Thread.sleep(1000)
        device.waitForIdle()
        device.click(500, 600)
        Thread.sleep(10000)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        device.findObject(By.text("History"))?.click()
        Thread.sleep(1000)
        device.waitForIdle()
        device.click(500, 500)
        Thread.sleep(10000)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        device.findObject(By.text("Browse"))?.click()
        Thread.sleep(1000)
        device.waitForIdle()
        device.click(500, 900)
        Thread.sleep(10000)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        device.findObject(By.text("Feed"))?.click()
        Thread.sleep(5000)
        device.waitForIdle()
        device.click(200, 700)
        Thread.sleep(5000)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        device.findObject(By.text("Extensions"))?.click()
        Thread.sleep(500)
        device.waitForIdle()
        device.findObject(By.text("Migrate"))?.click()
        Thread.sleep(500)
        device.waitForIdle()
        device.findObject(By.text("More"))?.click()
        Thread.sleep(500)
        device.waitForIdle()
        device.findObject(By.text("Settings"))?.click()
        Thread.sleep(500)
        device.waitForIdle()

        Thread.sleep(5000)
        device.waitForIdle()
        assertNotNull(
            "App window should still be present",
            device.findObject(By.pkg(APP_PACKAGE).depth(0)),
        )
    }
}
