package eu.kanade.tachiyomi.ui.library

import android.content.Context
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.model.Track

class RemoteTrackerLibraryTest {

    @Test
    fun buildTrackedRemoteKeys_onlyIncludesFavoriteLibraryTracks() {
        val favorites = listOf(
            libraryItem(id = 10L),
            libraryItem(id = 30L),
        )
        val tracksMap = mapOf(
            10L to listOf(track(mangaId = 10L, trackerId = 1L, remoteId = 100L)),
            20L to listOf(track(mangaId = 20L, trackerId = 2L, remoteId = 200L)),
            30L to listOf(track(mangaId = 30L, trackerId = TrackerManager.ANILIST, remoteId = 300L)),
        )

        val result = buildTrackedRemoteKeys(favorites, tracksMap)

        assertEquals(
            setOf(
                RemoteTrackKey(1L, 100L),
                RemoteTrackKey(TrackerManager.ANILIST, 300L),
            ),
            result,
        )
    }

    @Test
    fun buildRemoteTrackerLibraryItems_excludesTrackedAndUnsupportedEntries() {
        val trackerManager = trackerManager(
            tracker(trackerId = 1L, trackerName = "MyAnimeList"),
            tracker(trackerId = TrackerManager.ANILIST, trackerName = "AniList"),
        )

        val result = buildRemoteTrackerLibraryItems(
            remoteTracks = listOf(
                remoteTrack(trackerId = 1L, remoteId = 100L, title = "Tracked"),
                remoteTrack(
                    trackerId = TrackerManager.ANILIST,
                    remoteId = 200L,
                    title = "Keep Me",
                    lastChapterRead = 5.0,
                    totalChapters = 12L,
                ),
                remoteTrack(trackerId = 99L, remoteId = 300L, title = "Unsupported"),
            ),
            trackedKeys = setOf(RemoteTrackKey(1L, 100L)),
            trackerManager = trackerManager,
            context = mockk(relaxed = true),
            searchQuery = null,
        )

        assertEquals(1, result.size)
        assertEquals("Keep Me", result.single().track.title)
        assertEquals("AniList", result.single().trackerName)
        assertEquals("AL", result.single().trackerShortName)
        assertEquals("5/12", result.single().progressText)
    }

    @Test
    fun buildRemoteTrackerLibraryItems_sortsAndFiltersByQuery() {
        val trackerManager = trackerManager(
            tracker(trackerId = 1L, trackerName = "MyAnimeList"),
            tracker(trackerId = TrackerManager.ANILIST, trackerName = "AniList"),
        )
        val remoteTracks = listOf(
            remoteTrack(
                trackerId = 1L,
                remoteId = 100L,
                title = "Alpha",
                lastChapterRead = 1.0,
                totalChapters = 10L,
            ),
            remoteTrack(
                trackerId = TrackerManager.ANILIST,
                remoteId = 200L,
                title = "alpha",
                lastChapterRead = 5.0,
                totalChapters = 12L,
            ),
            remoteTrack(trackerId = 1L, remoteId = 300L, title = "Zeta"),
        )

        val sorted = buildRemoteTrackerLibraryItems(
            remoteTracks = remoteTracks,
            trackedKeys = emptySet(),
            trackerManager = trackerManager,
            context = mockk(relaxed = true),
            searchQuery = null,
        )

        assertEquals(
            listOf(
                "${TrackerManager.ANILIST}:200",
                "1:100",
                "1:300",
            ),
            sorted.map { it.key },
        )

        val byTracker = buildRemoteTrackerLibraryItems(
            remoteTracks = remoteTracks,
            trackedKeys = emptySet(),
            trackerManager = trackerManager,
            context = mockk(relaxed = true),
            searchQuery = "MyAnimeList",
        )
        assertEquals(listOf("Alpha", "Zeta"), byTracker.map { it.track.title })

        val byProgress = buildRemoteTrackerLibraryItems(
            remoteTracks = remoteTracks,
            trackedKeys = emptySet(),
            trackerManager = trackerManager,
            context = mockk(relaxed = true),
            searchQuery = "5/12",
        )
        assertEquals(listOf("alpha"), byProgress.map { it.track.title })
    }

    @Test
    fun remoteTrackerItemMatchesQuery_checksAllVisibleFields() {
        val item = RemoteTrackerLibraryItem(
            track = remoteTrack(trackerId = 1L, remoteId = 100L, title = "Blue Box"),
            trackerName = "MyAnimeList",
            trackerShortName = "MAL",
            statusText = "Reading",
            progressText = "7/12",
        )

        assertTrue(item.matchesQuery("blue"))
        assertTrue(item.matchesQuery("anime"))
        assertTrue(item.matchesQuery("reading"))
        assertTrue(item.matchesQuery("7/12"))
        assertFalse(item.matchesQuery("dropped"))
    }

    @Test
    fun remoteTrackerProgressText_formatsOnlyAvailableValues() {
        assertEquals(
            "3/10",
            remoteTrackerProgressText(remoteTrack(trackerId = 1L, remoteId = 1L, title = "A", lastChapterRead = 3.0, totalChapters = 10L)),
        )
        assertEquals(
            "3",
            remoteTrackerProgressText(remoteTrack(trackerId = 1L, remoteId = 2L, title = "B", lastChapterRead = 3.0)),
        )
        assertEquals(
            null,
            remoteTrackerProgressText(remoteTrack(trackerId = 1L, remoteId = 3L, title = "C")),
        )
    }

    @Test
    fun trackerBadgeLabel_usesKnownShortNames() {
        assertEquals("MAL", trackerBadgeLabel(1L, "MyAnimeList"))
        assertEquals("AL", trackerBadgeLabel(TrackerManager.ANILIST, "AniList"))
        assertEquals("Custom", trackerBadgeLabel(99L, "Custom"))
    }

    private fun trackerManager(vararg trackers: BaseTracker): TrackerManager {
        val trackersById = trackers.associateBy { it.id }
        val trackerManager = mockk<TrackerManager>()
        trackersById.forEach { (trackerId, tracker) ->
            every { trackerManager.get(trackerId) } returns tracker
        }
        every { trackerManager.get(match { it !in trackersById }) } returns null
        return trackerManager
    }

    private fun tracker(
        trackerId: Long,
        trackerName: String,
    ): BaseTracker {
        return mockk(relaxed = true) {
            every { this@mockk.id } returns trackerId
            every { this@mockk.name } returns trackerName
            every { getStatus(any()) } returns null
        }
    }

    private fun remoteTrack(
        trackerId: Long,
        remoteId: Long,
        title: String,
        lastChapterRead: Double = 0.0,
        totalChapters: Long = 0L,
    ) = RemoteTrackerTrack(
        trackerId = trackerId,
        remoteId = remoteId,
        title = title,
        coverUrl = "",
        status = 999L,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        trackingUrl = "",
    )

    private fun libraryItem(id: Long): LibraryItem {
        return LibraryItem(
            libraryManga = LibraryManga(
                manga = Manga.create().copy(
                    id = id,
                    source = 1L,
                    ogTitle = "Manga $id",
                ),
                categories = emptyList(),
                totalChapters = 0L,
                readCount = 0L,
                bookmarkCount = 0L,
                bookmarkReadCount = 0L,
                chapterFlags = 0L,
                latestUpload = 0L,
                chapterFetchedAt = 0L,
                lastRead = 0L,
            ),
            sourceManager = mockk<SourceManager>(relaxed = true),
        )
    }

    private fun track(
        mangaId: Long,
        trackerId: Long,
        remoteId: Long,
    ) = Track(
        id = remoteId,
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = remoteId,
        libraryId = null,
        title = "Track $remoteId",
        lastChapterRead = 0.0,
        totalChapters = 0L,
        status = 0L,
        score = 0.0,
        remoteUrl = "",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )
}
