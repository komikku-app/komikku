package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.track.interactor.InsertTrack

class AddTracksTest {

    @Test
    fun bind_assignsMangaIdBeforeBindingAndInsert() = runBlocking {
        val insertTrack = mockk<InsertTrack>()
        val syncChapterProgressWithTrack = mockk<SyncChapterProgressWithTrack>()
        val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
        val trackerManager = mockk<TrackerManager>(relaxed = true)
        val tracker = mockk<Tracker>()

        val trackSearch = TrackSearch.create(1L).apply {
            remote_id = 321L
            title = "Blue Box"
            tracking_url = "https://example.com/track/321"
        }
        val insertedTrack = slot<tachiyomi.domain.track.model.Track>()

        coEvery { getChaptersByMangaId.await(42L) } returns emptyList()
        coEvery { tracker.bind(trackSearch, false) } returns trackSearch
        coEvery { insertTrack.await(capture(insertedTrack)) } returns Unit
        coEvery { syncChapterProgressWithTrack.await(42L, any(), tracker) } returns null

        AddTracks(
            insertTrack = insertTrack,
            syncChapterProgressWithTrack = syncChapterProgressWithTrack,
            getChaptersByMangaId = getChaptersByMangaId,
            trackerManager = trackerManager,
        ).bind(
            tracker = tracker,
            item = trackSearch,
            mangaId = 42L,
        )

        assertEquals(42L, trackSearch.manga_id)
        assertEquals(42L, insertedTrack.captured.mangaId)
        coVerify(exactly = 1) { tracker.bind(match { it.manga_id == 42L }, false) }
    }
}
