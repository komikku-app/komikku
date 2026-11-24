package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.io.File

/**
 * Headless UI test of saved searches in the desktop catalogue (P13), stored in the shared `saved_search` table with
 * Android's filter serialisation: save the current search under a name (a duplicate name is rejected) → listed →
 * applying one restores its query and filters → delete.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueSavedSearchUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(FilterSource())) }
    }

    private fun StringResource.text(): String = localized()

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 10_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun savedSearches() = runBlocking { Injekt.get<GetSavedSearchBySourceId>().await(FilterSource.ID) }

    @Test
    fun `save, list, apply and delete a saved search`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        // A saved search with a non-default filter, stored like Android stores one
        val completedFilters = FilterSource().getFilterList().apply { (first() as Filter.CheckBox).state = true }
        runBlocking {
            Injekt.get<InsertSavedSearch>().await(
                SavedSearch(
                    id = -1,
                    source = FilterSource.ID,
                    name = "Completed done",
                    query = "done",
                    filtersJson = Json.encodeToString(FilterSerializer().serialize(completedFilters)),
                ),
            )
        }

        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.browse.text()).performClick()
        waitForText("Filter source")
        onNodeWithText("Filter source").performClick()
        waitForText("Filter popular")
        waitForText("Completed done")

        // Search, then save it under a name
        onNode(hasSetTextAction()).performTextInput("abc")
        onNodeWithContentDescription(MR.strings.action_search.text()).performClick()
        waitForText("Result for abc")
        onNodeWithTag("catalogue-save-search").performClick()
        onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("My abc")
        onNodeWithText(MR.strings.action_ok.text()).performClick()
        waitForText("My abc")
        waitUntil(timeoutMillis = 5_000) { savedSearches().any { it.name == "My abc" } }
        val stored = savedSearches().single { it.name == "My abc" }
        stored.query shouldBe "abc"
        stored.filtersJson!! shouldContain "Completed"

        // A duplicate name is rejected like on Android
        onNodeWithTag("catalogue-save-search").performClick()
        onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("My abc")
        onNodeWithText(MR.strings.action_ok.text()).performClick()
        waitForText(SYMR.strings.save_search_invalid_name.text())
        onNodeWithText(MR.strings.action_cancel.text()).performClick()
        savedSearches().count { it.name == "My abc" } shouldBe 1

        // Applying restores the query and the filters
        onNode(hasSetTextAction()).performTextClearance()
        onNode(hasSetTextAction()).performTextInput("xyz")
        onNodeWithContentDescription(MR.strings.action_search.text()).performClick()
        waitForText("Result for xyz")
        onNodeWithText("Completed done").performClick()
        waitForText("Result for done (completed)")
        onNodeWithText("My abc").performClick()
        waitForText("Result for abc")
        onAllNodesWithText("Result for abc (completed)").fetchSemanticsNodes().size shouldBe 0

        // Delete
        onNodeWithTag("saved-search-delete-My abc").performClick()
        waitForText(SYMR.strings.save_search_delete.text())
        onNodeWithText(MR.strings.action_ok.text()).performClick()
        waitUntil(timeoutMillis = 5_000) { savedSearches().none { it.name == "My abc" } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("My abc").fetchSemanticsNodes().isEmpty() }
        savedSearches().map { it.name } shouldBe listOf("Completed done")
    }
}

/** A catalogue source with one check box filter; search results show the query and whether the filter was set. */
private class FilterSource : CatalogueSource {
    override val id: Long = ID
    override val name: String = "Filter source"
    override val lang: String = "en"
    override val supportsLatest: Boolean = false

    private class Completed : Filter.CheckBox("Completed")

    override fun getFilterList(): FilterList = FilterList(Completed())

    private fun manga(title: String) = SManga.create().apply {
        url = "/filter/${title.lowercase().replace(' ', '-')}"
        this.title = title
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(manga("Filter popular")), hasNextPage = false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), hasNextPage = false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val completed = filters.filterIsInstance<Filter.CheckBox>().any { it.state }
        return MangasPage(listOf(manga("Result for $query" + if (completed) " (completed)" else "")), hasNextPage = false)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(manga, emptyList())

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    companion object {
        const val ID = 9013L
    }
}
