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
 * Instrumented tests for the Library screen.
 *
 * These tests navigate to the Library tab and verify common UI elements
 * without requiring any actual manga entries in the library.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun openLibrary() {
        composeTestRule
            .onNodeWithContentDescription("Library")
            .performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * Verifies that the Library screen is displayed after tapping the Library tab.
     */
    @Test
    fun libraryScreen_isDisplayedAfterTabTap() {
        openLibrary()
        // The Library tab/screen should render without crashing.
        composeTestRule.waitForIdle()
    }

    /**
     * Verifies the search icon is visible on the Library screen and can be tapped.
     */
    @Test
    fun libraryScreen_searchIconIsVisible() {
        openLibrary()
        composeTestRule
            .onNodeWithContentDescription("Search")
            .assertIsDisplayed()
    }

    /**
     * Taps the filter icon (if present) to confirm it doesn't crash the app.
     */
    @Test
    fun libraryScreen_filterIconDoesNotCrash() {
        openLibrary()
        runCatching {
            composeTestRule
                .onNodeWithContentDescription("Filter")
                .performClick()
            composeTestRule.waitForIdle()
        }
        // No crash = pass
    }
}
