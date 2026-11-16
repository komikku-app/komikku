package app.komikku.desktop.ui

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.window.DesktopShortcuts
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Headless UI test of the desktop shell (offscreen Compose scene, no window / display needed): real
 * Injekt graph and database, one category with one library manga.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopShellTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        runBlocking {
            val categoryId = Injekt.get<CategoryRepository>().insert(
                Category(id = 0, name = "Reading", order = 1, flags = 0, hidden = false),
            )
            val manga = Injekt.get<MangaRepository>().insertNetworkManga(
                listOf(
                    Manga.create().copy(
                        source = LocalSource.ID,
                        url = "desktop-manga",
                        ogTitle = "Desktop manga",
                        favorite = true,
                        initialized = true,
                    ),
                ),
            ).single()
            Injekt.get<MangaRepository>().setMangaCategories(manga.id, listOf(categoryId))
        }
    }

    @Test
    fun `shell shows the rail, the library and switches destinations`() = runDesktopComposeUiTest(width = 1200, height = 800) {
        val shortcuts = DesktopShortcuts(isMacOs = false)
        setContent { KomikkuApp(context = app, shortcuts = shortcuts) }

        // Rail destinations
        listOf(MR.strings.label_library, MR.strings.label_recent_updates, MR.strings.label_recent_manga, MR.strings.browse, MR.strings.label_more)
            .forEach { onNodeWithText(it.text()).assertExists() }

        // Library: real data from the database
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Desktop manga").fetchSemanticsNodes().isNotEmpty() }

        // More destination through the rail
        onNodeWithText(MR.strings.label_more.text()).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText(MR.strings.label_download_queue.text()).fetchSemanticsNodes().isNotEmpty()
        }

        // Browse through the keyboard shortcut scaffold (Ctrl+4)
        runOnIdle { shortcuts.handler!!(DesktopShortcuts.Action.SelectTab(3)) }
        val localSourceName = Injekt.get<SourceManager>().get(LocalSource.ID)!!.name
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(localSourceName).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun StringResource.text(): String = localized()
}
