package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.DesktopTheme
import app.komikku.desktop.ui.DesktopToastHost
import cafe.adriel.voyager.navigator.Navigator
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetSavedSearchBySourceId
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Headless UI tests of the desktop catalogue's filters (P13 review fixes): a saved search's filters do not outlive
 * the listing they were applied to, searches take the source's current filter list, a throwing filter list is a
 * source error, the last saved search applied wins, and unsubmitted search text survives a trip to the details.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogueFilterUiTest {

    private lateinit var app: Application
    private val source = ChangingFiltersSource()

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        app = DesktopInjekt.init(dataDir)
        Injekt.addSingletonFactory<SourceManager> { FixedSourceManager(listOf(source)) }
    }

    @BeforeEach
    fun reset() {
        source.version = 1
        source.throwFilters = false
        source.blockNext.set(null)
        source.blocked = false
        runBlocking {
            Injekt.get<GetSavedSearchBySourceId>().await(ChangingFiltersSource.ID)
                .forEach { Injekt.get<DeleteSavedSearchById>().await(it.id) }
        }
    }

    private fun StringResource.text(): String = localized()

    private fun ComposeUiTest.waitForText(text: String, timeoutMillis: Long = 10_000) {
        waitUntil(timeoutMillis = timeoutMillis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.hasText(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.catalogue() = setContent {
        CompositionLocalProvider(LocalContext provides app) {
            DesktopTheme { DesktopToastHost { Navigator(CatalogueScreen(ChangingFiltersSource.ID)) } }
        }
    }

    /** The saved searches are in the filter panel (opened from the toolbar). */
    private fun ComposeUiTest.openSavedSearches() {
        if (onAllNodesWithTag("catalogue-filter").fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithTag("catalogue-filter").performClick()
        }
    }

    private fun ComposeUiTest.search(query: String) {
        onNode(hasSetTextAction()).performTextClearance()
        onNode(hasSetTextAction()).performTextInput(query)
        onNodeWithContentDescription(MR.strings.action_search.text()).performClick()
    }

    private fun ComposeUiTest.searchFieldText(): String =
        onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun saveSearch(name: String, query: String, completed: Boolean) = runBlocking {
        val filters = ChangingFiltersSource().getFilterList().apply { (first() as Filter.CheckBox).state = completed }
        Injekt.get<InsertSavedSearch>().await(
            SavedSearch(
                id = -1,
                source = ChangingFiltersSource.ID,
                name = name,
                query = query,
                filtersJson = Json.encodeToString(FilterSerializer().serialize(filters)),
            ),
        )
    }

    @Test
    fun `a saved search's filters do not stay active after switching to popular`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        saveSearch("Completed one", query = "one", completed = true)
        catalogue()
        waitForText("Changing popular")
        openSavedSearches()
        waitForText("Completed one")
        onAllNodesWithText("Completed one").onFirst().performClick()
        waitForText("Found one (completed) [v1]")

        onNodeWithText(MR.strings.popular.text()).performClick()
        waitForText("Changing popular")
        search("two")
        waitForText("Found two")
        hasText("Found two [v1]") shouldBe true
        hasText("(completed)") shouldBe false
    }

    @Test
    fun `the filter panel edits a copy, applies, keeps the filters for typed searches and resets`() =
        runDesktopComposeUiTest(width = 1280, height = 900) {
            catalogue()
            waitForText("Changing popular")
            search("paged")
            waitForText("Found paged [v1]")
            onAllNodesWithTag("catalogue-filter-active", useUnmergedTree = true).fetchSemanticsNodes().size shouldBe 0

            onNodeWithTag("catalogue-filter").performClick()
            waitForText("Completed")
            // Android's rows: header, check box, tri-state, text, select; a select without options is only its name
            waitForText("Version 1")
            waitForText("Empty select")
            waitForText("Order")
            waitForText("Colour")
            waitForText("Author")
            onNodeWithText("Completed").performClick()

            // Editing changes nothing in the running search until "Filter": its next page has the old filters
            onNodeWithText(KMR.strings.desktop_load_more.text()).performClick()
            waitForText("Found paged p2 [v1]")
            hasText("(completed)") shouldBe false

            onNodeWithTag("catalogue-apply-filters").performClick()
            waitForText("Found paged (completed) [v1]")
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("catalogue-filter-panel").fetchSemanticsNodes().isEmpty() }
            onAllNodesWithTag("catalogue-filter-active", useUnmergedTree = true).fetchSemanticsNodes().size shouldBe 1

            // A typed search keeps the applied filters (Android's `search(query)`), the badge says so
            search("kept")
            waitForText("Found kept (completed) [v1]")
            onAllNodesWithTag("catalogue-filter-active", useUnmergedTree = true).fetchSemanticsNodes().size shouldBe 1

            // Reset returns to the source's defaults; "Filter" applies them
            onNodeWithTag("catalogue-filter").performClick()
            waitForText("Completed")
            onNodeWithText(MR.strings.action_reset.text()).performClick()
            waitForText("Completed")
            onNodeWithTag("catalogue-apply-filters").performClick()
            waitForText("Found kept [v1]")
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("catalogue-filter-active", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        }

    @Test
    fun `searches take the source's current filter list`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        catalogue()
        waitForText("Changing popular")
        // e.g. a JavaScript source whose filters follow its preferences
        source.version = 2
        search("fresh")
        waitForText("Found fresh")
        hasText("Found fresh [v2]") shouldBe true
    }

    @Test
    fun `a filter list that throws is a source error, not a crash`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        source.throwFilters = true
        catalogue()
        waitForText("Changing popular")
        search("broken")
        waitForText("android/webkit/WebView")
        hasText("Found broken") shouldBe false
    }

    @Test
    fun `the saved search applied last wins over a slower earlier one`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        saveSearch("Slow", query = "slow", completed = false)
        saveSearch("Quick", query = "quick", completed = false)
        catalogue()
        waitForText("Changing popular")
        openSavedSearches()
        waitForText("Quick")

        val gate = CountDownLatch(1)
        source.blockNext.set(gate)
        onAllNodesWithText("Slow").onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { source.blocked }
        // The panel closes on apply; open it again for the second search
        if (!hasText("Quick")) openSavedSearches()
        waitForText("Quick")
        onAllNodesWithText("Quick").onFirst().performClick()
        waitForText("Found quick")
        gate.countDown()
        waitForIdle()
        Thread.sleep(500)
        waitForIdle()
        hasText("Found slow") shouldBe false
        hasText("Found quick") shouldBe true
    }

    @Test
    fun `unsubmitted search text survives opening an entry and coming back`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        catalogue()
        waitForText("Changing popular")
        search("abc")
        waitForText("Found abc [v1]")
        onNode(hasSetTextAction()).performTextInput("def")
        searchFieldText() shouldBe "abcdef"

        onNodeWithText("Found abc [v1]").performClick()
        // The details screen replaces the catalogue (no search field there)
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        onNodeWithContentDescription(MR.strings.action_bar_up_description.text()).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        searchFieldText() shouldBe "abcdef"
    }
}

/**
 * A catalogue source whose filter list can change ([version], shown in the results as "[v<n>]"), throw ([throwFilters])
 * or block once on a gate ([blockNext]); search results show the query, the check box and the filter list version.
 */
private class ChangingFiltersSource : CatalogueSource {
    override val id: Long = ID
    override val name: String = "Changing filters"
    override val lang: String = "en"
    override val supportsLatest: Boolean = false

    @Volatile var version = 1

    @Volatile var throwFilters = false

    @Volatile var blocked = false
    val blockNext = AtomicReference<CountDownLatch?>(null)

    private class Completed : Filter.CheckBox("Completed")
    private class Version(version: Int) : Filter.Header("Version $version")

    /** A select without options: Android's sheet would crash on it (`SelectItem` reads `options[state]`). */
    private class EmptySelect : Filter.Select<String>("Empty select", emptyArray())
    private class Order : Filter.Select<String>("Order", arrayOf("Name", "Date"))
    private class Colour : Filter.TriState("Colour")
    private class Author : Filter.Text("Author")

    override fun getFilterList(): FilterList {
        blockNext.getAndSet(null)?.let { gate ->
            blocked = true
            gate.await(10, TimeUnit.SECONDS)
        }
        if (throwFilters) throw NoClassDefFoundError("android/webkit/WebView")
        return FilterList(Completed(), Version(version), EmptySelect(), Order(), Colour(), Author())
    }

    private fun manga(title: String) = SManga.create().apply {
        url = "/changing/${title.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
        this.title = title
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(manga("Changing popular")), hasNextPage = false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), hasNextPage = false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val completed = filters.filterIsInstance<Filter.CheckBox>().any { it.state }
        val version = filters.filterIsInstance<Filter.Header>().firstOrNull()?.name?.removePrefix("Version ")
        val suffix = (if (completed) " (completed)" else "") + " [v$version]"
        val title = "Found $query" + (if (page > 1) " p$page" else "") + suffix
        return MangasPage(listOf(manga(title)), hasNextPage = query == "paged" && page == 1)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(manga, emptyList())

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    companion object {
        const val ID = 9014L
    }
}
