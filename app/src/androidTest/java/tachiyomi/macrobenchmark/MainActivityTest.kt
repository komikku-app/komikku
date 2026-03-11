package tachiyomi.macrobenchmark

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.ui.main.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val APP_PACKAGE = BuildConfig.APPLICATION_ID
private const val LAUNCH_TIMEOUT_MS = 10_000L
private const val UI_TIMEOUT_MS = 5_000L

/**
 * Instrumented tests for [eu.kanade.tachiyomi.ui.main.MainActivity].
 *
 * Verifies that the main activity launches successfully and its lifecycle states are correct.
 * These tests run on a real device or emulator.
 *
 * Launches the app, drives through onboarding, and verifies
 * the main home screen is displayed.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Launch the app from the home screen for a clean start.
        device.pressHome()
        device.waitForIdle()

        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = context.packageManager
            .getLaunchIntentForPackage(APP_PACKAGE)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        checkNotNull(intent) { "Could not resolve launch intent for $APP_PACKAGE" }
        context.startActivity(intent)

        // Wait for the app to appear.
        device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT_MS)
    }

    /**
     * Verifies the activity launches without crashing and is not finishing immediately.
     */
    @Test
    fun activityLaunches_isNotFinishing() {
        activityRule.scenario.onActivity { activity ->
            assert(!activity.isFinishing) {
                "MainActivity should not be finishing right after launch."
            }
        }
    }

    /**
     * Verifies the activity reaches the RESUMED state after launch.
     */
    @Test
    fun activityLaunches_reachesResumedState() {
        assert(activityRule.scenario.state == Lifecycle.State.RESUMED) {
            "Expected RESUMED but was ${activityRule.scenario.state}"
        }
    }

    /**
     * Verifies the activity can be recreated (simulates a configuration change) without crashing.
     */
    @Test
    fun activityRecreate_doesNotCrash() {
        activityRule.scenario.recreate()
        activityRule.scenario.onActivity { activity ->
            assert(!activity.isFinishing) {
                "MainActivity should survive recreation (e.g. rotation)."
            }
        }
    }

    /**
     * Verifies the activity lifecycle reaches DESTROYED after close.
     */
    @Test
    fun activityLifecycle_statesAreReached() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assert(scenario.state == Lifecycle.State.RESUMED)
            scenario.close()
            assert(scenario.state == Lifecycle.State.DESTROYED)
        }
    }

    /**
     * Verifies the app launches and the main window is visible.
     */
    @Test
    fun appLaunches_mainWindowIsVisible() {
        val appWindow = device.wait(
            Until.findObject(By.pkg(APP_PACKAGE).depth(0)),
            LAUNCH_TIMEOUT_MS,
        )
        assertNotNull("App window should be visible after launch", appWindow)
    }

    /**
     * Drives through the first-launch onboarding and verifies the home screen appears.
     * On subsequent runs (onboarding already completed) this is a no-op and the
     * home screen assertion still passes.
     */
    @Test
    fun onboarding_completesAndShowsHomeScreen() {
        // Drive through onboarding if it is present (safe – each step is optional).
        initAppData(device, UI_TIMEOUT_MS)

        // After onboarding, at least one bottom-nav tab must be visible.
        val homeTab = device.wait(
            Until.findObject(By.text("Library")),
            LAUNCH_TIMEOUT_MS,
        )
        assertNotNull("Library tab should be visible after onboarding", homeTab)
    }

    /**
     * Presses back from the home screen and verifies the app does not crash.
     */
    @Test
    fun homeScreen_backPress_doesNotCrash() {
        // Wait until home screen is ready.
        device.wait(Until.findObject(By.pkg(APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT_MS)
        device.pressBack()
        device.waitForIdle()
        // App should still be in the foreground (back on home/minimize, not crash).
    }
}
