package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.interactor.DeleteFeedSavedSearchById
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetFeedSavedSearchBySourceId
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Headless UI tests of the desktop source feed (P13, Android's `SourceFeedScreen`): opened from the sources list, rows
 * for latest / popular / the source's own feed saved searches, the rows' headers and the search field leading into the
 * catalogue, ordering and deleting the saved-search rows, and adding a saved search to the source's feed from the
 * catalogue's filter panel within Android's limit.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourceFeedUiTest {

    private lateinit var app: Application
    private val source = LandingSource()

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(source)) }
    }

    @BeforeEach
    fun clear() = runBlocking {
        source.calls.set(0)
        Injekt.get<GetFeedSavedSearchGlobal>().await().forEach { Injekt.get<DeleteFeedSavedSearchById>().await(it.id) }
        sourceFeeds().forEach { Injekt.get<DeleteFeedSavedSearchById>().await(it.id) }
        Injekt.get<GetSavedSearchBySourceId>().await(LandingSource.ID).forEach { Injekt.get<DeleteSavedSearchById>().await(it.id) }
    }

    private fun StringResource.text(): String = localized()

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 10_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.shows(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.back() {
        onNodeWithContentDescription(MR.strings.action_bar_up_description.text()).performClick()
    }

    private fun ComposeUiTest.openSourceFeed() {
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.browse.text()).performClick()
        waitForText(LandingSource.NAME)
        // Also listed as the last used source after the first test
        onAllNodesWithText(LandingSource.NAME).onFirst().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("source-feed-rows").fetchSemanticsNodes().isNotEmpty() }
    }

    /** A row's header in the source feed ("Browse" is also the navigation rail's label). */
    private fun ComposeUiTest.rowHeader(title: String) =
        onNode(hasText(title) and hasAnyAncestor(hasTestTag("source-feed-rows")))

    private fun ComposeUiTest.waitForCatalogue() {
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("catalogue-filter").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.catalogueQuery(): String =
        onNode(hasSetTextAction() and !hasTestTag("source-feed-query")).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun savedSearch(name: String, query: String): SavedSearch = runBlocking {
        Injekt.get<InsertSavedSearch>().await(SavedSearch(id = -1, source = LandingSource.ID, name = name, query = query, filtersJson = null))
        Injekt.get<GetSavedSearchBySourceId>().await(LandingSource.ID).single { it.name == name }
    }

    private fun feedRow(savedSearchId: Long, global: Boolean = false) = runBlocking {
        Injekt.get<InsertFeedSavedSearch>().await(
            FeedSavedSearch(id = -1, source = LandingSource.ID, savedSearch = savedSearchId, global = global, feedOrder = 0),
        )
    }

    private fun sourceFeeds() = runBlocking { Injekt.get<GetFeedSavedSearchBySourceId>().await(LandingSource.ID) }

    @Test
    fun `the source feed's rows and search field lead into the catalogue`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        feedRow(savedSearch("Own search", query = "own").id)
        // A global feed row of the source is not in its own feed
        feedRow(savedSearch("Global search", query = "global").id, global = true)
        openSourceFeed()

        // Latest, Browse (popular), the source's own saved search; a failing row would show its error
        waitForText("L latest 1")
        waitForText("L popular")
        waitForText("L search own")
        waitForText("Own search")
        shows("Global search") shouldBe false

        // Headers open the catalogue: latest…
        rowHeader(MR.strings.latest.text()).performClick()
        waitForCatalogue()
        waitForText("L latest 1")
        shows("L popular") shouldBe false
        back()
        // …popular ("Browse")…
        waitForText("Own search")
        rowHeader(MR.strings.browse.text()).performClick()
        waitForCatalogue()
        waitForText("L popular")
        back()
        // …the saved search with its query
        waitForText("Own search")
        onNodeWithText("Own search").performClick()
        waitForCatalogue()
        waitForText("L search own")
        catalogueQuery() shouldBe "own"
        back()

        // The search field: the catalogue searches it
        waitForText("Own search")
        onNodeWithTag("source-feed-query").performTextInput("typed")
        onNodeWithTag("source-feed-search").performClick()
        waitForCatalogue()
        waitForText("L search typed")
        catalogueQuery() shouldBe "typed"
    }

    @Test
    fun `entries of the source feed are added to the library in bulk`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        openSourceFeed()
        waitForText("L latest 2")
        waitForText("L popular")
        onNodeWithTag("bulk-select").performClick()
        onNodeWithText("L latest 2").performClick()
        onNodeWithText("L popular").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("bulk-add").performClick()
        waitUntil(timeoutMillis = 10_000) { favorite("L latest 2") && favorite("L popular") }
        favorite("L latest 1") shouldBe false
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("bulk-select").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun favorite(title: String) = runBlocking {
        Injekt.get<GetManga>().await("/landing/${title.lowercase().replace(' ', '-')}", LandingSource.ID)?.favorite == true
    }

    @Test
    fun `saved-search rows are ordered and deleted, loaded rows are kept`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        val first = savedSearch("First", query = "one")
        val second = savedSearch("Second", query = "boom")
        feedRow(first.id)
        feedRow(second.id)
        openSourceFeed()
        waitForText("L search one")
        // A failing row shows the source's error
        waitForText("Search boom failed")
        waitForText("L popular")
        waitForIdle()
        source.calls.get() shouldBe 4

        onNodeWithTag("source-feed-sort").performClick()
        val secondRow = sourceFeeds().single { it.savedSearch == second.id }
        onNodeWithTag("feed-up-${secondRow.id}").performClick()
        waitUntil(timeoutMillis = 5_000) { sourceFeeds().map { it.savedSearch } == listOf(second.id, first.id) }

        val firstRow = sourceFeeds().single { it.savedSearch == first.id }
        onNodeWithTag("feed-delete-${firstRow.id}").performClick()
        waitForText(SYMR.strings.feed_delete.text())
        onNode(hasText(MR.strings.action_delete.text()) and hasAnyAncestor(isDialog())).performClick()
        waitUntil(timeoutMillis = 5_000) { sourceFeeds().map { it.savedSearch } == listOf(second.id) }
        onNodeWithTag("source-feed-sort-done").performClick()
        waitForText("L popular")
        shows("L search one") shouldBe false
        waitForIdle()
        // Reordering and deleting loaded nothing again
        source.calls.get() shouldBe 4

        // Refresh loads every row again
        onNodeWithTag("source-feed-refresh").performClick()
        waitUntil(timeoutMillis = 5_000) { source.calls.get() == 7 }
    }

    @Test
    fun `a saved search is added to the source's feed from the catalogue's filter panel within Android's limit`() =
        runDesktopComposeUiTest(width = 1280, height = 1000) {
            savedSearch("Panel search", query = "panel")
            openSourceFeed()
            waitForText("L popular")

            // The filter button opens the catalogue with its filter panel (Android's filter sheet)
            onNodeWithTag("source-feed-filter").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("catalogue-filter-panel").fetchSemanticsNodes().isNotEmpty() }
            waitForText("Panel search")
            onNodeWithTag("saved-search-feed-Panel search").performClick()
            // Android's confirmation, then the row is in the source's own feed
            waitForText(SYMR.strings.feed_add.text().substringBefore("%"))
            onNodeWithText(MR.strings.action_add.text()).performClick()
            waitUntil(timeoutMillis = 5_000) { sourceFeeds().size == 1 }
            sourceFeeds().single().global shouldBe false
            runBlocking { Injekt.get<GetFeedSavedSearchGlobal>().await() }.size shouldBe 0
            back()
            waitForText("L search panel")

            // Android's limit (MaxFeedItems = 20): the 21st is refused with its message
            // Named to sort after it: the panel lists the saved searches by name
            repeat(19) { feedRow(savedSearch("Z extra $it", query = "x$it").id) }
            waitUntil(timeoutMillis = 10_000) { sourceFeeds().size == 20 }
            onNodeWithTag("source-feed-filter").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("catalogue-filter-panel").fetchSemanticsNodes().isNotEmpty() }
            waitForText("Panel search")
            onNodeWithTag("saved-search-feed-Panel search").performClick()
            waitForText(KMR.strings.too_many_in_feed.text())
            shows(SYMR.strings.feed_add.text().substringBefore("%")) shouldBe false
            sourceFeeds().size shouldBe 20
        }
}

/** A catalogue source: "L latest n" / "L popular" / "L search <query>", a search for "boom" fails. [calls] counts requests. */
private class LandingSource : CatalogueSource {
    override val id: Long = ID
    override val name: String = NAME
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    val calls = AtomicInteger()

    private fun page(vararg titles: String): MangasPage {
        calls.incrementAndGet()
        return MangasPage(
            titles.map { t ->
                SManga.create().apply {
                    url = "/landing/${t.lowercase().replace(' ', '-')}"
                    title = t
                }
            },
            hasNextPage = false,
        )
    }

    override suspend fun getPopularManga(page: Int): MangasPage = page("L popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = page("L latest 1", "L latest 2")

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        if (query == "boom") {
            calls.incrementAndGet()
            throw java.io.IOException("Search boom failed")
        }
        return page("L search $query")
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(manga, emptyList())

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    companion object {
        const val ID = 9031L
        const val NAME = "Landing source"
    }
}
