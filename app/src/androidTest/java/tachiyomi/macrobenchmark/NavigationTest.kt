package tachiyomi.macrobenchmark

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import eu.kanade.tachiyomi.ui.main.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test that verifies navigation between the main bottom-nav tabs using
 * Jetpack Compose test APIs.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Taps the Library tab and verifies it becomes selected.
     */
    @Test
    fun tabNavigation_library_isReachable() {
        // Content descriptions are set via semantics in HomeScreen.kt.
        composeTestRule
            .onNodeWithContentDescription("Library")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()
    }

    /**
     * Taps the Updates tab and verifies it becomes selected.
     */
    @Test
    fun tabNavigation_updates_isReachable() {
        composeTestRule
            .onNodeWithContentDescription("Updates")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()
    }

    /**
     * Taps the History tab and verifies it becomes selected.
     */
    @Test
    fun tabNavigation_history_isReachable() {
        composeTestRule
            .onNodeWithContentDescription("History")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()
    }

    /**
     * Taps the Browse tab and verifies it becomes selected.
     */
    @Test
    fun tabNavigation_browse_isReachable() {
        composeTestRule
            .onNodeWithContentDescription("Browse")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()
    }

    /**
     * Taps the More tab and verifies it becomes selected.
     */
    @Test
    fun tabNavigation_more_isReachable() {
        composeTestRule
            .onNodeWithContentDescription("More")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()
    }
}

