package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.anilist.dto.ALEdge
import eu.kanade.tachiyomi.data.track.anilist.dto.ALFuzzyDate
import eu.kanade.tachiyomi.data.track.anilist.dto.ALItemTitle
import eu.kanade.tachiyomi.data.track.anilist.dto.ALSearchItem
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStaff
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStaffName
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStaffNode
import eu.kanade.tachiyomi.data.track.anilist.dto.ALUserListItem
import eu.kanade.tachiyomi.data.track.anilist.dto.ItemCover
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALUserListResult
import eu.kanade.tachiyomi.ui.library.toRemoteTrackerTrack
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackerRemoteEntryMappingTest {

    @Test
    fun anilistUserListItem_mapsToTrackSearch() {
        val result = ALUserListItem(
            id = 55L,
            status = "CURRENT",
            scoreRaw = 80,
            progress = 12,
            startedAt = ALFuzzyDate(year = 2024, month = 1, day = 2),
            completedAt = ALFuzzyDate(year = null, month = null, day = null),
            media = ALSearchItem(
                id = 1234L,
                title = ALItemTitle("Blue Box"),
                coverImage = ItemCover("https://example.com/blue-box.jpg"),
                description = null,
                format = "MANGA",
                status = "RELEASING",
                startDate = ALFuzzyDate(year = 2021, month = 4, day = 1),
                chapters = 150L,
                averageScore = 91,
                staff = ALStaff(
                    edges = listOf(
                        ALEdge("Story", ALStaffNode(ALStaffName(userPreferred = "Author", native = null, full = null))),
                    ),
                ),
            ),
            private = true,
        )
            .toALUserManga()
            .toTrackSearch()

        assertEquals(TrackerManager.ANILIST, result.tracker_id)
        assertEquals(1234L, result.remote_id)
        assertEquals("Blue Box", result.title)
        assertEquals(Anilist.READING, result.status)
        assertEquals(12.0, result.last_chapter_read)
        assertEquals(150L, result.total_chapters)
        assertEquals("https://example.com/blue-box.jpg", result.cover_url)
        assertEquals(AnilistApi.mangaUrl(1234L), result.tracking_url)
        assertEquals(80.0, result.score)
        assertEquals(55L, result.library_id)
        assertEquals(true, result.private)
    }

    @Test
    fun malUserListResult_deserializesMyListStatusAlias() {
        val json = Json { ignoreUnknownKeys = true }
        val result = json.decodeFromString<MALUserListResult>(
            """
            {
              "data": [
                {
                  "node": {
                    "id": 321,
                    "title": "Blue Box",
                    "num_chapters": 100,
                    "main_picture": null,
                    "status": "finished",
                    "media_type": "manga",
                    "start_date": null
                  },
                  "my_list_status": {
                    "is_rereading": true,
                    "status": "reading",
                    "num_chapters_read": 40,
                    "score": 9,
                    "start_date": "2024-01-01",
                    "finish_date": null
                  }
                }
              ],
              "paging": {
                "next": null
              }
            }
            """.trimIndent(),
        )

        val entry = result.data.single()
        assertEquals(321L, entry.node.id)
        assertEquals("Blue Box", entry.node.title)
        assertEquals(100L, entry.node.numChapters)
        assertEquals(true, entry.listStatus?.isRereading)
        assertEquals("reading", entry.listStatus?.status)
        assertEquals(40.0, entry.listStatus?.numChaptersRead)
        assertEquals(9, entry.listStatus?.score)
        assertEquals("2024-01-01", entry.listStatus?.startDate)
        assertNull(entry.listStatus?.finishDate)
    }

    @Test
    fun trackSearch_mapsToRemoteTrackerTrack_withoutDroppingFields() {
        val track = TrackSearch.create(TrackerManager.ANILIST).apply {
            remote_id = 9001L
            title = "Remote Title"
            cover_url = "https://example.com/cover.jpg"
            status = 7L
            last_chapter_read = 22.0
            total_chapters = 24L
            tracking_url = "https://example.com/title"
        }

        val result = track.toRemoteTrackerTrack()

        assertEquals(TrackerManager.ANILIST, result.trackerId)
        assertEquals(9001L, result.remoteId)
        assertEquals("Remote Title", result.title)
        assertEquals("https://example.com/cover.jpg", result.coverUrl)
        assertEquals(7L, result.status)
        assertEquals(22.0, result.lastChapterRead)
        assertEquals(24L, result.totalChapters)
        assertEquals("https://example.com/title", result.trackingUrl)
    }
}
