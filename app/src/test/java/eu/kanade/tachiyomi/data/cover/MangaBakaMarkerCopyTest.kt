package eu.kanade.tachiyomi.data.cover

import eu.kanade.tachiyomi.data.cache.CoverCache
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MangaBakaMarkerCopyTest {

    @TempDir
    lateinit var dir: File

    private val coverCache by lazy {
        mockk<CoverCache> {
            every { getCustomCoverFile(any()) } answers { File(dir, firstArg<Long?>().toString()) }
        }
    }

    private fun cover(mangaId: Long, modified: Long) = coverCache.getCustomCoverFile(mangaId).apply {
        writeText("image")
        setLastModified(modified)
    }

    private fun marker(mangaId: Long) = readMangaBakaMarker(mangaBakaMarkerFile(coverCache.getCustomCoverFile(mangaId)))

    private fun writeMarker(mangaId: Long, marker: MangaBakaMarker) =
        mangaBakaMarkerFile(coverCache.getCustomCoverFile(mangaId)).writeText(marker.serialize())

    @Test
    fun copiedMangaBakaCoverStaysManaged() {
        cover(1, 1_000_000L)
        val written = MangaBakaMarker(3248, "29", 500L, 1_000_000L, "ja", "anilist/30642")
        writeMarker(1, written)
        val copy = cover(2, 2_000_000L)

        copyMangaBakaCoverMarker(1, 2, coverCache)

        assertEquals(written.copy(coverModified = copy.lastModified()), marker(2))
    }

    @Test
    fun userCoverIsNotClaimed() {
        cover(1, 1_000_000L)
        // The user replaced the cover after MangaBaka wrote it.
        writeMarker(1, MangaBakaMarker(3248, "29", 500L, 900_000L, "ja", ""))
        cover(2, 2_000_000L)

        copyMangaBakaCoverMarker(1, 2, coverCache)

        assertFalse(mangaBakaMarkerFile(coverCache.getCustomCoverFile(2)).exists())
    }

    @Test
    fun unfinishedOrMissingMarkerIsNotCopied() {
        cover(1, 1_000_000L)
        cover(2, 2_000_000L)
        copyMangaBakaCoverMarker(1, 2, coverCache)
        assertFalse(mangaBakaMarkerFile(coverCache.getCustomCoverFile(2)).exists())

        writeMarker(1, MangaBakaMarker(3248, "29", 0L, MangaBakaMarker.WRITING, "ja", ""))
        copyMangaBakaCoverMarker(1, 2, coverCache)
        assertFalse(mangaBakaMarkerFile(coverCache.getCustomCoverFile(2)).exists())
    }

    @Test
    fun markerIsReplacedWhole() {
        val markerFile = mangaBakaMarkerFile(coverCache.getCustomCoverFile(1))
        writeMangaBakaMarker(markerFile, MangaBakaMarker(1, "5", 1L, 2L, "ja", ""))
        val next = MangaBakaMarker(1, "6", 3L, 4L, "ja", "anilist/1")
        writeMangaBakaMarker(markerFile, next)
        assertEquals(next, readMangaBakaMarker(markerFile))
        assertFalse(File(dir, markerFile.name + ".tmp").exists())
    }
}
