package eu.kanade.tachiyomi.source.mangafire

import exh.ui.metadata.adapters.descriptionWithAltTitles
import exh.ui.metadata.adapters.mergeGenreChips
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MangaFireMetadataOverlayTest {

    @BeforeEach
    fun clearCache() {
        MangaFireMetadataOverlay.clearCache()
    }

    @Test
    fun `extractHid from new title url`() {
        assertEquals("72opj", MangaFireMetadataOverlay.extractHid("/title/72opj-renge-to-naruto"))
        assertEquals(
            "72opj",
            MangaFireMetadataOverlay.extractHid("https://mangafire.to/title/72opj-renge-to-naruto/"),
        )
    }

    @Test
    fun `extractHid from legacy manga url`() {
        assertEquals("72opj", MangaFireMetadataOverlay.extractHid("/manga/renge-to-naruto.72opj"))
        assertEquals(
            "abc12",
            MangaFireMetadataOverlay.extractHid("https://mangafire.to/manga/some-slug.abc12"),
        )
    }

    @Test
    fun `extractHid without slug separator`() {
        assertEquals("72opj", MangaFireMetadataOverlay.extractHid("/title/72opj"))
    }

    @Test
    fun `parses known API fields and score aliases`() {
        val body = """
            {
              "data": {
                "title": "Renge to Naruto!",
                "altTitles": ["Renge to Naruto!", "れんげとなると!"],
                "year": 2023,
                "score": 9.5,
                "score_count": 11,
                "follows_total": 3500,
                "content_rating": "erotica"
              }
            }
        """.trimIndent()

        val meta = MangaFireMetadataOverlay.parseApiResponse(body)!!

        assertEquals(9.5f, meta.rating)
        assertEquals(11, meta.voteCount)
        assertEquals(3500, meta.follows)
        assertEquals(2023, meta.year)
        assertEquals("Erotica", meta.contentRating)
        assertEquals(listOf("Renge to Naruto!", "れんげとなると!"), meta.altTitles)
    }

    @Test
    fun `parses rating and bookmarks aliases`() {
        val body = """
            {
              "data": {
                "rating": 8.2,
                "votes": 42,
                "bookmarks": 1200,
                "contentRating": "suggestive",
                "alt_titles": "Alt A · Alt B"
              }
            }
        """.trimIndent()

        val meta = MangaFireMetadataOverlay.parseApiResponse(body)!!

        assertEquals(8.2f, meta.rating)
        assertEquals(42, meta.voteCount)
        assertEquals(1200, meta.follows)
        assertEquals("Suggestive", meta.contentRating)
        assertEquals(listOf("Alt A", "Alt B"), meta.altTitles)
    }

    @Test
    fun `parseApiResponse returns null for non-json`() {
        assertNull(MangaFireMetadataOverlay.parseApiResponse("<html>challenge</html>"))
    }

    @Test
    fun `parses title HTML rating box and stats`() {
        val html = """
            <html><body>
              <div class="info">
                <h1>Renge and Naruto!</h1>
                <h6>Renge to Naruto! · れんげとなると!</h6>
                <span>2023</span>
                <span>EROTICA</span>
              </div>
              <div class="rating-box" data-score="9.5">9.5 /10 (11)</div>
              <div>3.5K follows</div>
              <div>#2478 ranked</div>
            </body></html>
        """.trimIndent()

        val meta = MangaFireMetadataOverlay.parseTitleHtml(html)!!

        assertEquals(9.5f, meta.rating)
        assertEquals(11, meta.voteCount)
        assertEquals(3500, meta.follows)
        assertEquals(2023, meta.year)
        assertEquals("Erotica", meta.contentRating)
        assertTrue(meta.altTitles.contains("Renge to Naruto!"))
        assertTrue(meta.altTitles.any { it.contains("れんげ") })
    }

    @Test
    fun `parseCompactCount handles K suffix`() {
        assertEquals(3500, MangaFireMetadataOverlay.parseCompactCount("3.5K"))
        assertEquals(12, MangaFireMetadataOverlay.parseCompactCount("12"))
        assertNull(MangaFireMetadataOverlay.parseCompactCount("nope"))
    }

    @Test
    fun `mergeGenreChips prepends Year and Content Rating like MangaDex`() {
        val meta = MangaFireOverlayMetadata(year = 2023, contentRating = "Erotica")
        assertEquals(
            listOf("Year: 2023", "Content Rating: Erotica", "Action", "Romance"),
            meta.mergeGenreChips(listOf("Action", "Romance")),
        )
    }

    @Test
    fun `mergeGenreChips skips Safe and existing year tags`() {
        val meta = MangaFireOverlayMetadata(year = 2023, contentRating = "Safe")
        assertEquals(listOf("2023", "Action"), meta.mergeGenreChips(listOf("2023", "Action")))
        assertNull(MangaFireOverlayMetadata().mergeGenreChips(null))
    }

    @Test
    fun `descriptionWithAltTitles appends bullet list`() {
        val meta = MangaFireOverlayMetadata(altTitles = listOf("Alt A", "Alt B"))
        assertEquals(
            "Synopsis here\n\nAlternative titles:\n• Alt A\n• Alt B",
            meta.descriptionWithAltTitles("Synopsis here", "Alternative titles"),
        )
        assertEquals(
            "Alternative titles:\n• Alt A\n• Alt B",
            meta.descriptionWithAltTitles(null, "Alternative titles"),
        )
        assertEquals(
            "Already has Alternative titles inside",
            meta.descriptionWithAltTitles("Already has Alternative titles inside", "Alternative titles"),
        )
    }
}
