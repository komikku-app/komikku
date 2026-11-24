package app.komikku.desktop.ui.more

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.DesktopTheme
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P23, headless: the library's category screen (P20 / P21 review fix 5, until now only compile-checked): the "Add"
 * button leaves the last category's buttons uncovered at the end of a long list.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryCategoriesUiTest {

    private lateinit var app: Application
    private val names = (1..25).map { "Cat %02d".format(it) }

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
        runBlocking { names.forEach { Injekt.get<CreateCategoryWithName>().await(it) } }
    }

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `the add button leaves the last category's buttons uncovered`() =
        runDesktopComposeUiTest(width = 900, height = 600) {
            setContent {
                CompositionLocalProvider(LocalContext provides app) {
                    DesktopTheme { Navigator(CategoriesScreen()) { CurrentScreen() } }
                }
            }
            waitForTag("category-${names.first()}")
            // To the end of the list (the last row as far up as the list lets it go)
            onNodeWithTag("categories").performScrollToIndex(names.lastIndex)
            waitForIdle()
            val add = onNodeWithTag("category-add").fetchSemanticsNode().boundsInRoot
            listOf("up", "down", "hide", "rename", "delete").forEach { action ->
                val button = onNodeWithTag("category-$action-${names.last()}").fetchSemanticsNode().boundsInRoot
                withClue("$action $button, add $add") { (button.bottom <= add.top) shouldBe true }
            }
        }
}
