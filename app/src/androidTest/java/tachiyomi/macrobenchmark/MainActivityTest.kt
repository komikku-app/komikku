package tachiyomi.macrobenchmark

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import eu.kanade.tachiyomi.ui.main.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

fun initAppData() {
    // Click Next
    // Click "Select a folder"
    // Click "USE THIS FOLDER"
    // Click ALLOW
    // Click Next
    // Click first Grant => Allow from this source => Back
    // Click second Grant => Allow
    // Next
    // Get started
}

/**
 * Instrumented test for [MainActivity].
 *
 * Verifies that the main activity launches successfully and its lifecycle states are correct.
 * These tests run on a real device or emulator.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

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
}
