package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import eu.kanade.domain.ui.UiPreferences
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
import tachiyomi.domain.source.interactor.GetFeedSavedSearchGlobal
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertFeedSavedSearch
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.repository.FeedSavedSearchRepository
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
 * Headless UI tests of the desktop Feed tab (P13): the rows of the shared `feed_saved_search` table (latest / popular /
 * saved search, loading one at a time, a failing row's error), opening a row in the catalogue and an entry in the
 * details, adding (source → latest / popular / a saved search), reordering, deleting, and Android's row limit.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedUiTest {

    private lateinit var app: Application
    private val latestSource = FeedSource(ID_A, "Feed source A", supportsLatest = true)
    private val popularSource = FeedSource(ID_B, "Feed source B", supportsLatest = false)
    private val brokenSource = FeedSource(ID_C, "Feed source C", supportsLatest = true, error = "Feed boom")

    /** Installed later (or never) by the tests. */
    private val laterSource = FeedSource(ID_D, "Feed source D", supportsLatest = true)
    private val baseSources = listOf(latestSource, popularSource, brokenSource)
    private val sourceManager = FixedSourceManager(baseSources)
    private lateinit var feedRepository: FlakyFeedRepository

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        Injekt.addSingletonFactory<SourceManager> { sourceManager }
        feedRepository = FlakyFeedRepository(Injekt.get<FeedSavedSearchRepository>())
        Injekt.addSingletonFactory { GetFeedSavedSearchGlobal(feedRepository) }
    }

    @BeforeEach
    fun clear() = runBlocking {
        sourceManager.setSources(baseSources)
        feedRepository.failSubscriptions.set(0)
        sourceManager.failingIds = emptySet()
        (baseSources + laterSource).forEach { it.calls.set(0) }
        Injekt.get<UiPreferences>().hideFeedTab().set(false)
        Injekt.get<UiPreferences>().feedTabInFront().set(false)
        Injekt.get<GetFeedSavedSearchGlobal>().await().forEach { Injekt.get<DeleteFeedSavedSearchById>().await(it.id) }
        listOf(ID_A, ID_B, ID_C, ID_D).forEach { id ->
            Injekt.get<GetSavedSearchBySourceId>().await(id).forEach { Injekt.get<DeleteSavedSearchById>().await(it.id) }
        }
    }

    private fun StringResource.text(): String = localized()

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 10_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.shows(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.openBrowse() {
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.browse.text()).performClick()
        waitForText(MR.strings.label_extensions.text())
    }

    /** The Feed tab's label (exact match: the sources' names contain "Feed" too). */
    private fun ComposeUiTest.feedTabs() = onAllNodesWithText(SYMR.strings.feed.text()).fetchSemanticsNodes()

    private fun ComposeUiTest.openFeed() {
        openBrowse()
        waitUntil(timeoutMillis = 10_000) { feedTabs().isNotEmpty() }
        onNodeWithText(SYMR.strings.feed.text()).performClick()
    }

    /** Lets pending loads run (a load that should not happen would have happened by then). */
    private fun ComposeUiTest.settle() {
        waitForIdle()
        Thread.sleep(500)
        waitForIdle()
    }

    private fun savedSearch(sourceId: Long, name: String, query: String): Long = runBlocking {
        Injekt.get<InsertSavedSearch>().await(SavedSearch(id = -1, source = sourceId, name = name, query = query, filtersJson = null))
        Injekt.get<GetSavedSearchBySourceId>().await(sourceId).single { it.name == name }.id
    }

    private fun feedRow(sourceId: Long, savedSearchId: Long? = null) = runBlocking {
        Injekt.get<InsertFeedSavedSearch>().await(
            FeedSavedSearch(id = -1, source = sourceId, savedSearch = savedSearchId, global = true, feedOrder = 0),
        )
    }

    private fun feeds() = runBlocking { Injekt.get<GetFeedSavedSearchGlobal>().await() }

    private fun ComposeUiTest.back() {
        onNodeWithContentDescription(MR.strings.action_bar_up_description.text()).performClick()
    }

    @Test
    fun `rows show latest, popular, saved search and error results and open the catalogue and details`() =
        runDesktopComposeUiTest(width = 1280, height = 1000) {
            val searchId = savedSearch(ID_A, "A saved", query = "q")
            feedRow(ID_A)
            feedRow(ID_A, searchId)
            feedRow(ID_C)
            feedRow(ID_B)
            openFeed()

            // Latest for a source that has it, popular otherwise, a saved search's query, a failing row's error
            waitForText("A latest 1")
            waitForText("A search q")
            waitForText("Feed boom")
            waitForText("B popular")
            // Titles like Android's: the saved search's name (subtitle: the source), else the source (subtitle: language)
            waitForText("A saved")

            // An entry opens in the details
            onAllNodesWithText("A latest 1").onFirst().performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("feed-rows").fetchSemanticsNodes().isEmpty() }
            back()
            waitForText("A latest 1")

            // A saved search row opens the catalogue with that search
            onNodeWithText("A saved").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            waitForText("A search q")
            onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text shouldBe "q"
            back()
            waitForText("B popular")

            // A latest row opens the catalogue on its latest updates
            onAllNodesWithText("Feed source A").onFirst().performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            waitForText("A latest 1")
            shows("A popular") shouldBe false
        }

    @Test
    fun `add, reorder and delete rows within Android's limit`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        savedSearch(ID_A, "A saved", query = "q")
        openFeed()
        waitForText(SYMR.strings.feed_tab_empty.text())

        // Add: source B (no latest) → "Popular"
        onNodeWithTag("feed-add").performClick()
        waitForText("Feed source B")
        onNodeWithTag("feed-add-search").performTextInput("source B")
        waitUntil(timeoutMillis = 5_000) { !shows("Feed source A (") }
        onNode(hasText("Feed source B", substring = true) and hasAnyAncestor(isDialog())).performClick()
        onNodeWithText(MR.strings.action_ok.text()).performClick()
        waitForText(MR.strings.popular.text())
        onNode(hasText(MR.strings.popular.text()) and hasAnyAncestor(isDialog())).performClick()
        onNodeWithText(MR.strings.action_ok.text()).performClick()
        waitForText("B popular")

        // Add: source A → its saved search
        onNodeWithTag("feed-add").performClick()
        waitForText("Feed source A")
        onNode(hasText("Feed source A", substring = true) and hasAnyAncestor(isDialog())).performClick()
        onNodeWithText(MR.strings.action_ok.text()).performClick()
        waitForText("A saved")
        shows(MR.strings.latest.text()) shouldBe true
        onNode(hasText("A saved") and hasAnyAncestor(isDialog())).performClick()
        onNodeWithText(MR.strings.action_ok.text()).performClick()
        waitForText("A search q")
        feeds().map { it.source } shouldBe listOf(ID_B, ID_A)

        // Reorder: move the saved search row up
        onNodeWithTag("feed-sort").performClick()
        val searchRow = feeds().single { it.source == ID_A }
        onNodeWithTag("feed-up-${searchRow.id}").performClick()
        waitUntil(timeoutMillis = 5_000) { feeds().map { it.source } == listOf(ID_A, ID_B) }

        // Delete with Android's confirmation
        val popularRow = feeds().single { it.source == ID_B }
        onNodeWithTag("feed-delete-${popularRow.id}").performClick()
        waitForText(SYMR.strings.feed_delete.text())
        onNodeWithText(MR.strings.action_delete.text()).performClick()
        waitUntil(timeoutMillis = 5_000) { feeds().map { it.source } == listOf(ID_A) }
        onNodeWithTag("feed-sort-done").performClick()
        waitForText("A search q")
        shows("B popular") shouldBe false

        // Android's limit: no more rows once there are MaxFeedItems (20); a row is unique per source and saved search,
        // so the extra rows are saved searches of source B (the boundary itself: FeedItemsTest)
        repeat(19) { feedRow(ID_B, savedSearch(ID_B, "B saved $it", query = "b$it")) }
        waitUntil(timeoutMillis = 10_000) { feeds().size == 20 }
        onNodeWithTag("feed-add").performClick()
        waitForText(KMR.strings.too_many_in_feed.text())
        shows(MR.strings.action_ok.text()) shouldBe false
        feeds().size shouldBe 20
    }

    @Test
    fun `entries of several rows are added to the library in bulk`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        feedRow(ID_A)
        feedRow(ID_B)
        openFeed()
        waitForText("A latest 2")
        waitForText("B popular")
        onNodeWithTag("bulk-select").performClick()
        onNodeWithText("A latest 2").performClick()
        onNodeWithText("B popular").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("bulk-add").performClick()
        // No categories: added at once
        waitUntil(timeoutMillis = 10_000) { favorite(ID_A, "A latest 2") && favorite(ID_B, "B popular") }
        favorite(ID_A, "A latest 1") shouldBe false
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("feed-add").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun favorite(sourceId: Long, title: String) = runBlocking {
        Injekt.get<GetManga>().await("/feed/$sourceId/${title.lowercase().replace(' ', '-')}", sourceId)?.favorite == true
    }

    @Test
    fun `the add dialog's OK is disabled until a source is selected`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        openFeed()
        waitForText(SYMR.strings.feed_tab_empty.text())
        onNodeWithTag("feed-add").performClick()
        waitForText("Feed source B")
        onNodeWithText(MR.strings.action_ok.text()).assertIsNotEnabled()
        onNode(hasText("Feed source B", substring = true) and hasAnyAncestor(isDialog())).performClick()
        onNodeWithText(MR.strings.action_ok.text()).assertIsEnabled()
        // A search that hides the selected source clears the selection again
        onNodeWithTag("feed-add-search").performTextInput("nothing like this")
        waitUntil(timeoutMillis = 5_000) { !shows("Feed source B (") }
        onNodeWithText(MR.strings.action_ok.text()).assertIsNotEnabled()
    }

    @Test
    fun `a row whose source is installed later loads and opens`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        sourceManager.setSources(baseSources)
        feedRow(ID_A)
        feedRow(ID_D)
        openFeed()
        waitForText("A latest 1")
        // Not installed: the row is titled by the source id and has no results (like Android)
        waitForText(ID_D.toString())
        waitForText(MR.strings.no_results_found.text())

        sourceManager.setSources(baseSources + laterSource)
        waitForText("D latest 1")
        waitForText("Feed source D")
        // The other row was not loaded again
        latestSource.calls.get() shouldBe 1
        onAllNodesWithText("Feed source D").onFirst().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        waitForText("D latest 1")
    }

    @Test
    fun `a feed that fails to be read shows an error and keeps following the rows`() =
        runDesktopComposeUiTest(width = 1280, height = 1000) {
            // Building the rows throws (here: the lookup of a row's source)
            sourceManager.failingIds = setOf(ID_E)
            feedRow(ID_A)
            feedRow(ID_E)
            openFeed()
            waitForText(MR.strings.internal_error.text())

            // Later changes of the feed are still followed
            sourceManager.failingIds = emptySet()
            feedRow(ID_B)
            waitForText("B popular")
            waitForText("A latest 1")
            waitForText(ID_E.toString())
        }

    @Test
    fun `a feed whose rows cannot be read recovers on Refresh`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        feedRow(ID_A)
        feedRepository.failSubscriptions.set(1)
        openFeed()
        waitForText(MR.strings.internal_error.text())

        // Refresh follows the feed again: the rows are built and load, later changes are followed
        onNodeWithTag("feed-refresh").performClick()
        waitForText("A latest 1")
        feedRow(ID_B)
        waitForText("B popular")
    }

    @Test
    fun `order mode ends when its last row is deleted`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        feedRow(ID_A)
        openFeed()
        waitForText("A latest 1")
        onNodeWithTag("feed-sort").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("feed-order").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("feed-delete-${feeds().single().id}").performClick()
        waitForText(SYMR.strings.feed_delete.text())
        onNodeWithText(MR.strings.action_delete.text()).performClick()
        waitUntil(timeoutMillis = 5_000) { feeds().isEmpty() }
        // The empty feed with its toolbar (add), not order mode
        waitForText(SYMR.strings.feed_tab_empty.text())
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("feed-add").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithTag("feed-sort-done").fetchSemanticsNodes().isEmpty() shouldBe true
    }

    @Test
    fun `reordering and deleting keep the loaded rows, adding loads only the new row`() =
        runDesktopComposeUiTest(width = 1280, height = 1000) {
            feedRow(ID_A)
            feedRow(ID_B)
            openFeed()
            waitForText("A latest 1")
            waitForText("B popular")
            settle()
            latestSource.calls.get() shouldBe 1
            popularSource.calls.get() shouldBe 1

            onNodeWithTag("feed-sort").performClick()
            val rowB = feeds().single { it.source == ID_B }
            onNodeWithTag("feed-up-${rowB.id}").performClick()
            waitUntil(timeoutMillis = 5_000) { feeds().map { it.source } == listOf(ID_B, ID_A) }
            val rowA = feeds().single { it.source == ID_A }
            onNodeWithTag("feed-delete-${rowA.id}").performClick()
            waitForText(SYMR.strings.feed_delete.text())
            onNodeWithText(MR.strings.action_delete.text()).performClick()
            waitUntil(timeoutMillis = 5_000) { feeds().map { it.source } == listOf(ID_B) }
            onNodeWithTag("feed-sort-done").performClick()
            waitForText("B popular")

            feedRow(ID_C)
            waitForText("Feed boom")
            settle()
            popularSource.calls.get() shouldBe 1
            latestSource.calls.get() shouldBe 1
            brokenSource.calls.get() shouldBe 1

            // Refresh loads every row again
            onNodeWithTag("feed-refresh").performClick()
            waitUntil(timeoutMillis = 5_000) { popularSource.calls.get() == 2 && brokenSource.calls.get() == 2 }
        }

    @Test
    fun `the feed loads only once its tab is shown and follows Android's hide and position settings`() =
        runDesktopComposeUiTest(width = 1280, height = 1000) {
            feedRow(ID_A)
            openBrowse()
            settle()
            // Browse opens on Sources: the feed's rows are not loaded yet
            latestSource.calls.get() shouldBe 0
            onNodeWithText(SYMR.strings.feed.text()).performClick()
            waitForText("A latest 1")
            latestSource.calls.get() shouldBe 1

            // Feed first (Android's "feed tab in front")
            Injekt.get<UiPreferences>().feedTabInFront().set(true)
            waitUntil(timeoutMillis = 5_000) {
                val feed = feedTabs().map { it.boundsInRoot.left }
                val sources = onAllNodesWithText(MR.strings.label_sources.text()).fetchSemanticsNodes().map { it.boundsInRoot.left }
                feed.isNotEmpty() && sources.isNotEmpty() && feed.first() < sources.first()
            }

            // Hidden (Android's "hide feed tab")
            Injekt.get<UiPreferences>().hideFeedTab().set(true)
            waitUntil(timeoutMillis = 5_000) { feedTabs().isEmpty() }
            waitForText(MR.strings.label_extensions.text())
        }

    companion object {
        const val ID_A = 9021L
        const val ID_B = 9022L
        const val ID_C = 9023L
        const val ID_D = 9024L
        const val ID_E = 9025L
    }
}

/**
 * A catalogue source for the feed: "<A|B|C|D> latest n" / "… popular" / "… search <query>", or always [error]. [calls]
 * counts its listing requests.
 */
private class FeedSource(
    override val id: Long,
    override val name: String,
    override val supportsLatest: Boolean,
    private val error: String? = null,
) : CatalogueSource {
    override val lang: String = "en"

    val calls = AtomicInteger()

    private val letter = name.last()

    private fun page(vararg titles: String): MangasPage {
        calls.incrementAndGet()
        error?.let { throw java.io.IOException(it) }
        return MangasPage(
            titles.map { t ->
                SManga.create().apply {
                    url = "/feed/$id/${t.lowercase().replace(' ', '-')}"
                    title = t
                }
            },
            hasNextPage = false,
        )
    }

    override suspend fun getPopularManga(page: Int): MangasPage = page("$letter popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = page("$letter latest 1", "$letter latest 2")

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        page("$letter search $query")

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(manga, emptyList())

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}
