package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.DesktopTheme
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterItem
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The filter rows of the desktop catalogue (the desktop copy `SourceFilterItems.kt`), pinned to `master`'s
 * `SourceFilterDialog.FilterItem` (synced to 15610e67e4), including its fix for invalid filter states from extensions
 * (a222d3a220): Android and the desktop show a source's filters the same way.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourceFilterItemUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
    }

    private fun ComposeUiTest.rows(vararg filters: Filter<*>, onUpdate: () -> Unit = {}) = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme {
                Column { filters.forEach { SourceFilterItem(it, onUpdate, startExpanded = true) } }
            }
        }
    }

    private fun ComposeUiTest.shows(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a tri-state with an unknown state shows as ignored, and a click includes it`() = runDesktopComposeUiTest {
        val filter = object : Filter.TriState("Completed", 7) {}
        var updates = 0
        rows(filter) { updates++ }

        onNodeWithText("Completed").performClick()
        waitForIdle()

        filter.state shouldBe Filter.TriState.STATE_INCLUDE
        updates shouldBe 1
    }

    @Test
    fun `a tri-state cycles include, exclude, ignore`() = runDesktopComposeUiTest {
        val filter = object : Filter.TriState("Completed", Filter.TriState.STATE_INCLUDE) {}
        rows(filter)

        onNodeWithText("Completed").performClick()
        waitForIdle()
        filter.state shouldBe Filter.TriState.STATE_EXCLUDE
        onNodeWithText("Completed").performClick()
        waitForIdle()
        filter.state shouldBe Filter.TriState.STATE_IGNORE
    }

    @Test
    fun `a select whose state is out of range shows no value, and a select without values shows`() =
        runDesktopComposeUiTest {
            val outOfRange = object : Filter.Select<String>("Order", arrayOf("Newest", "Oldest"), 5) {}
            val empty = object : Filter.Select<String>("Status", emptyArray()) {}
            rows(outOfRange, empty)
            waitForIdle()

            shows("Order") shouldBe true
            shows("Newest") shouldBe false
            shows("Oldest") shouldBe false
            shows("Status") shouldBe true
        }
}
