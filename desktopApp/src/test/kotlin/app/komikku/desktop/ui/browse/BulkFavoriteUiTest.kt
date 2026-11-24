package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Headless UI tests of P14's browse leftovers: bulk favourites from the catalogue and from global search (selection
 * mode, select all, the duplicate check with "Skip all", the category dialog), and the extension name in global search
 * row titles.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BulkFavoriteUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(BulkSource())) }
    }

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 10_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }
    }

    private fun ComposeUiTest.show(screen: Screen) = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme { DesktopToastHost { Navigator(screen) } }
        }
    }

    private fun stored(title: String) = runBlocking {
        Injekt.get<GetManga>().await("/bulk/${title.lowercase().replace(' ', '-')}", BulkSource.ID)
    }

    private fun favorite(title: String) = stored(title)?.favorite == true

    private fun categoriesOf(title: String) = runBlocking {
        Injekt.get<GetCategories>().await(stored(title)!!.id).map { it.name }.toSet()
    }

    @Test
    fun `entries selected in the catalogue are added to the library`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        show(CatalogueScreen(BulkSource.ID))
        waitForText("Bulk A")
        onNodeWithTag("bulk-select").performClick()
        waitForTag("bulk-add")
        onNodeWithText("Bulk A").performClick()
        onNodeWithText("Bulk B").performClick()
        // Selected entries are marked; the bar counts them
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("cover-selected-${stored("Bulk A")!!.id}").fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty() }
        // Clicking a selected entry again unselects it, and selects it once more
        onNodeWithText("Bulk B").performClick()
        waitForTag("cover-selected-${stored("Bulk B")!!.id}", present = false)
        onNodeWithText("Bulk B").performClick()

        // No categories: added at once (Android's rule), the selection ends
        onNodeWithTag("bulk-add").performClick()
        waitUntil(timeoutMillis = 10_000) { favorite("Bulk A") && favorite("Bulk B") }
        favorite("Bulk C") shouldBe false
        waitForTag("bulk-select")
    }

    @Test
    fun `select inverse compares the entries by id`() {
        val model = BulkFavoriteScreenModel()
        val loaded = Manga.create().copy(id = 9_041_001L, source = BulkSource.ID, url = "/bulk/inverse-a", ogTitle = "Inverse A")
        val other = Manga.create().copy(id = 9_041_002L, source = BulkSource.ID, url = "/bulk/inverse-b", ogTitle = "Inverse B")
        model.toggleSelection(loaded)
        // The same entry after its row changed (e.g. its details were fetched): still the selected one
        val changed = loaded.copy(ogTitle = "Inverse A (updated)", favorite = true)
        model.reverseSelection(listOf(changed, other))
        model.state.value.selection.map { it.id } shouldBe listOf(other.id)
    }

    @Test
    fun `global search selects all, skips the duplicates and asks for categories`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        runBlocking { Injekt.get<CreateCategoryWithName>().await("Shelf") }
        // A library entry of another source with the title of one result: a duplicate
        TestLibrary.insertManga(OTHER_SOURCE, "/other/global-c", "Global C")
        show(GlobalSearchScreen("Global"))
        waitForText("Global A")
        waitForText("Global C")
        onNodeWithTag("bulk-select").performClick()
        waitForTag("bulk-toolbar")
        onNodeWithTag("bulk-select-all").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("3").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("bulk-add").performClick()

        // P10's duplicate dialog with Android's bulk choices: skip all duplicates
        waitForTag("duplicate-dialog")
        waitForTag("duplicate-allow-all")
        onNodeWithTag("duplicate-skip-all").performClick()

        // Categories exist and none is the default: Android's category dialog
        waitForText(MR.strings.action_move_category.localized())
        onNode(hasText("Shelf") and hasAnyAncestor(isDialog())).performClick()
        onNodeWithText(MR.strings.action_ok.localized()).performClick()
        waitUntil(timeoutMillis = 10_000) { favorite("Global A") && favorite("Global B") }
        waitUntil(timeoutMillis = 10_000) { categoriesOf("Global A") == setOf("Shelf") && categoriesOf("Global B") == setOf("Shelf") }
        favorite("Global C") shouldBe false
        waitForTag("bulk-toolbar", present = false)
    }

    @Test
    fun `the library search searches globally`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        TestLibrary.insertManga(OTHER_SOURCE, "/other/kept", "Kept entry")
        setContent { KomikkuApp(context = app) }
        waitForTag("library-search")
        onNodeWithTag("library-search").performTextInput("Lookup")
        // Android's "Search globally" item while searching: global search with the query
        waitForTag("library-global-search")
        onNodeWithTag("library-global-search").performClick()
        waitForText("Lookup A")
        onNodeWithTag("global-search-query").fetchSemanticsNode().config[SemanticsProperties.EditableText].text shouldBe "Lookup"
    }

    @Test
    fun `a global search row names the extension after the source when they differ`() {
        val source = BulkSource()
        fun extension(name: String) = Extension.Installed(
            name = name,
            pkgName = "app.komikku.test.bulk",
            versionName = "1.0",
            versionCode = 1,
            libVersion = 1.4,
            lang = "en",
            isNsfw = false,
            signatureHash = "test",
            pkgFactory = null,
            sources = listOf(StubSource(BulkSource.ID, "en", BulkSource.NAME)),
            icon = null,
            isShared = false,
        )
        globalSearchRowTitle(source, emptyList()) shouldBe BulkSource.NAME
        globalSearchRowTitle(source, listOf(extension(BulkSource.NAME))) shouldBe BulkSource.NAME
        globalSearchRowTitle(source, listOf(extension("Bulk sources"))) shouldBe "${BulkSource.NAME} (Bulk sources)"
    }

    companion object {
        const val OTHER_SOURCE = 9042L
    }
}

/** An online source without network: popular "Bulk A/B/C", a search for q "q A/B/C". */
private class BulkSource : HttpSource() {
    override val id: Long = ID
    override val name: String = NAME
    override val lang: String = "en"
    override val baseUrl: String = "https://bulk.example.org"
    override val supportsLatest: Boolean = false

    private fun page(vararg titles: String) = MangasPage(
        titles.map { t ->
            SManga.create().apply {
                url = "/bulk/${t.lowercase().replace(' ', '-')}"
                title = t
            }
        },
        hasNextPage = false,
    )

    override suspend fun getPopularManga(page: Int): MangasPage = page("Bulk A", "Bulk B", "Bulk C")

    override suspend fun getLatestUpdates(page: Int): MangasPage = page()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        page("$query A", "$query B", "$query C")

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(manga, emptyList())

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    companion object {
        const val ID = 9041L
        const val NAME = "Bulk source"
    }
}
