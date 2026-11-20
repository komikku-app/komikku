package eu.kanade.tachiyomi.data.cover

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaBakaRulesTest {

    private fun cover(index: String?, language: String = "ja", type: String = "volume", url: String? = "u$index") =
        buildString {
            append("""{"type":"$type","language":"$language"""")
            if (index != null) append(""","index":"$index"""")
            if (url != null) append(""","image":{"x350":{"x2":"$url"}}""")
            append("}")
        }

    private fun page(vararg covers: String): List<JsonObject> =
        Json.parseToJsonElement("[${covers.joinToString(",")}]").jsonArray.map { it.jsonObject }

    private fun latest(vararg covers: String, language: String = "ja") =
        MangaBakaRules.latestFrontCover(page(*covers), language)?.label

    @Test
    fun highestVolumeWins() {
        // Vinland Saga in Japanese lists alternate editions of volumes 1 and 2 under the same label.
        val covers = (listOf("1", "1", "2", "2") + (3..29).map { it.toString() }).map { cover(it) }
        assertEquals("29", latest(*covers.toTypedArray()))
        assertEquals("6", latest(cover("1"), cover("1.1"), cover("2"), cover("6")))
        assertEquals("10", latest(cover("10"), cover("10.5")))
    }

    @Test
    fun alternateEditionCountsWhenAlone() {
        // Berserk in Chinese only has "41.3".
        assertEquals("41.3", latest(cover("41.3")))
        assertEquals("3.1", latest(cover("2.1"), cover("3.1")))
    }

    @Test
    fun nonVolumeLabelsAreSkipped() {
        assertNull(latest(cover("1-3"), cover("Box Set"), cover(""), cover(null), cover("Vol. 2")))
        assertEquals("7", latest(cover("Infinity"), cover("NaN"), cover("1e3"), cover("7")))
        assertEquals("03", latest(cover("03"), cover("2")))
    }

    @Test
    fun onlyFrontCoversInTheLanguageCount() {
        assertEquals("5", latest(cover("5"), cover("9", type = "volume_back"), cover("8", language = "en")))
        assertEquals("4", latest(cover("4", language = "es-LA"), language = "es-la"))
        assertNull(latest(cover("9", type = "banner")))
    }

    @Test
    fun coversWithoutImageAreSkipped() {
        assertEquals("5", latest(cover("5"), cover("6", url = null)))
    }

    @Test
    fun mangaUpdatesIdsKeepLeadingZeros() {
        assertEquals("cql4c43", MangaBakaRules.mangaUpdatesId("cql4c43".toLong(36)))
        assertEquals("0onyq5w", MangaBakaRules.mangaUpdatesId("0onyq5w".toLong(36)))
        assertEquals("03vdvvx", MangaBakaRules.mangaUpdatesId("03vdvvx".toLong(36)))
    }

    @Test
    fun markerRoundTrip() {
        val marker = MangaBakaMarker(3248, "29", 1_000L, 2_000L, "ja", "anilist/30642")
        assertEquals(marker, MangaBakaMarker.parse(marker.serialize()))
        assertEquals("", MangaBakaMarker.parse("3248|29|1000|2000|ja").link)
        assertThrows(Exception::class.java) { MangaBakaMarker.parse("not a marker") }
    }

    @Test
    fun coverOwnership() {
        val written = MangaBakaMarker(1, "5", 1_000L, 2_000L, "ja", "")
        assertTrue(written.owns(2_000L))
        assertFalse(written.owns(2_001L)) // replaced by the user since
        val writing = written.copy(coverModified = MangaBakaMarker.WRITING)
        assertTrue(writing.owns(12_345L)) // a write cut short is still ours to replace
        assertTrue(written.isCover(1, "5", "ja"))
        assertFalse(writing.isCover(1, "5", "ja")) // and never counts as the finished cover
        assertFalse(written.isCover(1, "5", "en"))
    }
}
