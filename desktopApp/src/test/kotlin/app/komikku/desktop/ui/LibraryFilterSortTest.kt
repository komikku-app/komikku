package app.komikku.desktop.ui

import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.tabs.groupByCategory
import app.komikku.desktop.ui.tabs.hasActiveFilters
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.LibraryItemPreferences
import eu.kanade.tachiyomi.ui.library.applyLibraryFilters
import eu.kanade.tachiyomi.ui.library.applyLibrarySort
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.model.Track
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * The borrowed Android library filters and sort (`LibraryFilterSort.kt`) as the desktop library runs them:
 * tri-state filters, tracker and category filters, every sort mode with direction, per-category sort flags
 * and the global sort when the library is not grouped by category.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryFilterSortTest {

    private val items by lazy {
        listOf(
            // id, title, total, read, bookmarks, status, lastRead, dateAdded, latestUpload, source, categories
            item(1, "Alpha", total = 10, read = 10, bookmarks = 1, status = SManga.COMPLETED, lastRead = 5, dateAdded = 3, latestUpload = 1),
            item(2, "beta", total = 5, read = 2, lastRead = 9, dateAdded = 1, latestUpload = 7, categories = listOf(1, 2)),
            item(3, "Gamma", total = 4, read = 0, dateAdded = 2, latestUpload = 5, source = LocalSource.ID, categories = listOf(2)),
            item(4, "delta", total = 0, read = 0, dateAdded = 4, latestUpload = 3, categories = emptyList()),
        )
    }

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        DesktopInjekt.init(dataDir)
    }

    @Test
    fun `tri-state filters`() {
        filter(prefs(filterUnread = TriState.ENABLED_IS)) shouldContainExactly listOf(2L, 3L)
        filter(prefs(filterUnread = TriState.ENABLED_NOT)) shouldContainExactly listOf(1L, 4L)
        filter(prefs(filterStarted = TriState.ENABLED_IS)) shouldContainExactly listOf(1L, 2L)
        filter(prefs(filterBookmarked = TriState.ENABLED_IS)) shouldContainExactly listOf(1L)
        filter(prefs(filterCompleted = TriState.ENABLED_NOT)) shouldContainExactly listOf(2L, 3L, 4L)
        // Downloaded: local manga count as downloaded (nothing else is downloaded here)
        filter(prefs(filterDownloaded = TriState.ENABLED_IS)) shouldContainExactly listOf(3L)
        // The global "downloaded only" wins over the library filter
        filter(prefs(globalFilterDownloaded = true, filterDownloaded = TriState.ENABLED_NOT)) shouldContainExactly listOf(3L)
        // Combined
        filter(prefs(filterUnread = TriState.ENABLED_IS, filterStarted = TriState.ENABLED_IS)) shouldContainExactly listOf(2L)
        prefs().hasActiveFilters(emptyMap()) shouldBe false
        prefs(filterUnread = TriState.ENABLED_NOT).hasActiveFilters(emptyMap()) shouldBe true
        prefs().hasActiveFilters(mapOf(1L to TriState.ENABLED_IS)) shouldBe true
    }

    @Test
    fun `tracker and category filters`() {
        val tracks = mapOf(1L to listOf(track(1, trackerId = 1)), 2L to listOf(track(2, trackerId = 2)))
        filter(prefs(), tracks, trackingFilter = mapOf(1L to TriState.ENABLED_IS, 2L to TriState.DISABLED)) shouldContainExactly listOf(1L)
        filter(prefs(), tracks, trackingFilter = mapOf(1L to TriState.DISABLED, 2L to TriState.ENABLED_NOT)) shouldContainExactly listOf(1L, 3L, 4L)
        // Not logged in to any tracker: tracker filters are ignored
        filter(prefs(), tracks, trackingFilter = emptyMap()) shouldContainExactly listOf(1L, 2L, 3L, 4L)

        val categories = prefs(filterCategories = true)
        filter(categories, included = setOf(2)) shouldContainExactly listOf(2L, 3L)
        filter(categories, included = setOf(1, 2)) shouldContainExactly listOf(2L)
        filter(categories, excluded = setOf(1)) shouldContainExactly listOf(3L, 4L)
        // Disabled category filter ignores the sets
        filter(prefs(), included = setOf(2)) shouldContainExactly listOf(1L, 2L, 3L, 4L)
    }

    @Test
    fun `sort modes and directions`() {
        sort(LibrarySort.Type.Alphabetical, ascending = true) shouldContainExactly listOf(1L, 2L, 4L, 3L)
        sort(LibrarySort.Type.Alphabetical, ascending = false) shouldContainExactly listOf(3L, 4L, 2L, 1L)
        sort(LibrarySort.Type.TotalChapters, ascending = false) shouldContainExactly listOf(1L, 2L, 3L, 4L)
        sort(LibrarySort.Type.LastRead, ascending = false) shouldContainExactly listOf(2L, 1L, 4L, 3L)
        sort(LibrarySort.Type.DateAdded, ascending = true) shouldContainExactly listOf(2L, 3L, 1L, 4L)
        sort(LibrarySort.Type.LatestChapter, ascending = false) shouldContainExactly listOf(2L, 3L, 4L, 1L)
        // Unread count: entries with unread chapters first in both directions (Android rule), ties alphabetical
        sort(LibrarySort.Type.UnreadCount, ascending = true) shouldContainExactly listOf(2L, 3L, 1L, 4L)
        sort(LibrarySort.Type.UnreadCount, ascending = false) shouldContainExactly listOf(3L, 2L, 1L, 4L)
        // Random: seeded by the preference, stable for the same seed
        Injekt.get<LibraryPreferences>().randomSortSeed().set(42)
        val random = sort(LibrarySort.Type.Random, ascending = true)
        random shouldContainExactlyInAnyOrder listOf(1L, 2L, 3L, 4L)
        sort(LibrarySort.Type.Random, ascending = true) shouldContainExactly random
    }

    @Test
    fun `per-category sort flags and the global sort`() {
        val byTotal = Category(1, "By total", 1, LibrarySort(LibrarySort.Type.TotalChapters, LibrarySort.Direction.Descending).flag, false)
        val byTitle = Category(2, "By title", 2, LibrarySort.default.flag, false)
        val grouped = groupByCategory(listOf(byTotal, byTitle), items, showHiddenCategories = false)
            .let { map -> map.mapValues { (_, ids) -> ids.reversed() } }
        val sorted = grouped.applyLibrarySort(
            favoritesById = items.associateBy { it.id },
            trackMap = emptyMap(),
            loggedInTrackerIds = emptySet(),
            groupSort = null,
            libraryPreferences = Injekt.get(),
            trackerManager = Injekt.get<TrackerManager>(),
        )
        sorted[byTotal] shouldBe listOf(1L, 2L)
        sorted[byTitle] shouldBe listOf(2L, 3L)
        // Global sort (Android: library grouped by something else than categories)
        grouped.applyLibrarySort(
            favoritesById = items.associateBy { it.id },
            trackMap = emptyMap(),
            loggedInTrackerIds = emptySet(),
            groupSort = LibrarySort(LibrarySort.Type.Alphabetical, LibrarySort.Direction.Descending),
            libraryPreferences = Injekt.get(),
            trackerManager = Injekt.get<TrackerManager>(),
        )[byTitle] shouldBe listOf(3L, 2L)
    }

    private fun filter(
        preferences: LibraryItemPreferences,
        trackMap: Map<Long, List<Track>> = emptyMap(),
        trackingFilter: Map<Long, TriState> = emptyMap(),
        included: Set<Long> = emptySet(),
        excluded: Set<Long> = emptySet(),
    ): List<Long> = runBlocking {
        items.applyLibraryFilters(
            trackMap = trackMap,
            trackingFilter = trackingFilter,
            preferences = preferences,
            includedCategories = included,
            excludedCategories = excluded,
            downloadManager = Injekt.get<DownloadManager>(),
            getMergedMangaById = Injekt.get(),
        ).map { it.id }
    }

    private fun sort(type: LibrarySort.Type, ascending: Boolean): List<Long> {
        val category = Category(
            id = 1,
            name = "All",
            order = 1,
            flags = LibrarySort(type, if (ascending) LibrarySort.Direction.Ascending else LibrarySort.Direction.Descending).flag,
            hidden = false,
        )
        return mapOf(category to items.map { it.id }).applyLibrarySort(
            favoritesById = items.associateBy { it.id },
            trackMap = emptyMap(),
            loggedInTrackerIds = emptySet(),
            groupSort = null,
            libraryPreferences = Injekt.get(),
            trackerManager = Injekt.get<TrackerManager>(),
        ).getValue(category)
    }

    private fun prefs(
        globalFilterDownloaded: Boolean = false,
        filterDownloaded: TriState = TriState.DISABLED,
        filterUnread: TriState = TriState.DISABLED,
        filterStarted: TriState = TriState.DISABLED,
        filterBookmarked: TriState = TriState.DISABLED,
        filterCompleted: TriState = TriState.DISABLED,
        filterCategories: Boolean = false,
    ) = LibraryItemPreferences(
        downloadBadge = false,
        unreadBadge = true,
        localBadge = true,
        languageBadge = false,
        useLangIcon = false,
        sourceBadge = false,
        skipOutsideReleasePeriod = false,
        globalFilterDownloaded = globalFilterDownloaded,
        filterDownloaded = filterDownloaded,
        filterUnread = filterUnread,
        filterStarted = filterStarted,
        filterBookmarked = filterBookmarked,
        filterCompleted = filterCompleted,
        filterIntervalCustom = TriState.DISABLED,
        filterLewd = TriState.DISABLED,
        filterCategories = filterCategories,
    )

    private fun track(mangaId: Long, trackerId: Long) = Track(
        id = mangaId * 10 + trackerId,
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = 1,
        libraryId = null,
        title = "",
        lastChapterRead = 0.0,
        totalChapters = 0,
        status = 1,
        score = 0.0,
        remoteUrl = "",
        startDate = 0,
        finishDate = 0,
        private = false,
    )

    private fun item(
        id: Long,
        title: String,
        total: Long,
        read: Long,
        bookmarks: Long = 0,
        status: Int = SManga.ONGOING,
        lastRead: Long = 0,
        dateAdded: Long = 0,
        latestUpload: Long = 0,
        source: Long = 1L,
        categories: List<Long> = listOf(1),
    ) = LibraryItem(
        libraryManga = LibraryManga(
            // Not marked favorite: `Manga.title` would look up custom info.
            manga = Manga.create().copy(id = id, source = source, ogTitle = title, ogStatus = status.toLong(), dateAdded = dateAdded),
            categories = categories,
            totalChapters = total,
            readCount = read,
            bookmarkCount = bookmarks,
            bookmarkReadCount = 0,
            chapterFlags = 0,
            latestUpload = latestUpload,
            chapterFetchedAt = 0,
            lastRead = lastRead,
        ),
        unreadCount = total - read,
    )
}
