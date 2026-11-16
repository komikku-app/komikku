package app.komikku.desktop.ui

import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.tabs.groupByCategory
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.filterLibraryByQuery
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Library grouping and the borrowed Android text search (`filterLibraryByQuery` in `LibraryFilterSort.kt`)
 * on in-memory items; the DI graph provides the search engine, interactors and the compat `Application`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryPagesTest {

    private val default = category(Category.UNCATEGORIZED_ID, "", order = 0)
    private val reading = category(1, "Reading", order = 2)
    private val later = category(2, "Later", order = 1)
    private val hidden = category(3, "Hidden", order = 3, hidden = true)

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        DesktopInjekt.init(dataDir)
    }

    @Test
    fun `categories in user order, hidden ones only when shown`() {
        val items = listOf(
            item(1, "beta", listOf(1)),
            item(2, "Alpha", listOf(1, 2)),
            item(3, "Gamma", listOf(3)),
        )
        groupByCategory(listOf(default, reading, later, hidden), items, showHiddenCategories = false)
            .map { (category, ids) -> category.name to ids } shouldContainExactly
            listOf("Later" to listOf(2L), "Reading" to listOf(1L, 2L))
        groupByCategory(listOf(default, reading, later, hidden), items, showHiddenCategories = true)
            .keys.map { it.name } shouldContainExactly listOf("Later", "Reading", "Hidden")
    }

    @Test
    fun `default category only when it has manga or is the only one`() {
        groupByCategory(listOf(default), emptyList(), false).keys.map { it.id } shouldContainExactly listOf(0L)
        groupByCategory(listOf(default, reading), emptyList(), false).keys.map { it.id } shouldContainExactly listOf(1L)
        groupByCategory(listOf(default, reading), listOf(item(1, "A", emptyList())), false)
            .keys.map { it.id } shouldContainExactly listOf(0L, 1L)
    }

    @Test
    fun `search matches title, author, artist, description, genre parts and source id`() {
        val items = listOf(
            item(1, "Blue Period", listOf(1), author = "Yamaguchi", artist = "Tsubasa"),
            item(2, "Dungeon Meshi", listOf(1), genre = listOf("Comedy", "Dark Fantasy"), description = "Food"),
            item(3, "Frieren", listOf(2), source = 4242L),
        )
        search(items, "blue") shouldContainExactly listOf(1L)
        search(items, "YAMAGUCHI") shouldContainExactly listOf(1L)
        // Artist (the old desktop search missed nothing here, but the Android matcher is the reference)
        search(items, "tsubasa") shouldContainExactly listOf(1L)
        // Partial genre: Android uses `contains`, not `equals`
        search(items, "fanta") shouldContainExactly listOf(2L)
        search(items, "food") shouldContainExactly listOf(2L)
        search(items, "4242") shouldContainExactly listOf(3L)
        search(items, "") shouldContainExactly listOf(1L, 2L, 3L)
    }

    @Test
    fun `search trims, combines terms and supports exclusion, id and src queries`() {
        val items = listOf(
            item(1, "Blue Period", listOf(1), genre = listOf("Drama")),
            item(2, "Blue Lock", listOf(1), genre = listOf("Sports")),
            item(3, "Frieren", listOf(2), source = 7L),
            item(4, "Local chapters", listOf(2), source = LocalSource.ID),
        )
        search(items, "  blue  ") shouldContainExactly listOf(1L, 2L)
        // Every term must match
        search(items, "blue sports") shouldContainExactly listOf(2L)
        // `-term` excludes
        search(items, "blue -sports") shouldContainExactly listOf(1L)
        // Android quirk kept: a local-source entry never passes an exclusion term (its source id string is null)
        search(items, "-blue") shouldContainExactly listOf(3L)
        // Quoted phrase
        search(items, "\"blue lock\"") shouldContainExactly listOf(2L)
        search(items, "id:2") shouldContainExactly listOf(2L)
        search(items, "src:local") shouldContainExactly listOf(4L)
        search(items, "src:7") shouldContainExactly listOf(3L)
    }

    private fun search(items: List<LibraryItem>, query: String): List<Long> = runBlocking {
        filterLibraryByQuery(
            unfiltered = items,
            query = query,
            loggedInTrackServices = emptyMap(),
            searchEngine = Injekt.get(),
            getIdsOfFavoriteMangaWithMetadata = Injekt.get(),
            getTracks = Injekt.get(),
            sourceManager = Injekt.get(),
            getSearchTags = Injekt.get(),
            getSearchTitles = Injekt.get(),
            trackerManager = Injekt.get<TrackerManager>(),
        ).map { it.id }
    }

    private fun category(id: Long, name: String, order: Long, hidden: Boolean = false) =
        Category(id = id, name = name, order = order, flags = 0, hidden = hidden)

    private fun item(
        id: Long,
        title: String,
        categories: List<Long>,
        author: String? = null,
        artist: String? = null,
        description: String? = null,
        genre: List<String>? = null,
        source: Long = 1L,
    ) = LibraryItem(
        LibraryManga(
            // Not marked favorite: `Manga.title` would look up custom info.
            manga = Manga.create().copy(
                id = id,
                source = source,
                ogTitle = title,
                ogAuthor = author,
                ogArtist = artist,
                ogDescription = description,
                ogGenre = genre,
            ),
            categories = categories,
            totalChapters = 0,
            readCount = 0,
            bookmarkCount = 0,
            bookmarkReadCount = 0,
            chapterFlags = 0,
            latestUpload = 0,
            chapterFetchedAt = 0,
            lastRead = 0,
        ),
    )
}
