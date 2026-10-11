package eu.kanade.domain.source.service

import eu.kanade.domain.source.model.BlacklistedSeriesEntry
import eu.kanade.tachiyomi.util.lang.toBlacklistNormalizedTitle
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class SourcePreferencesTest {

    @Test
    fun `series blacklist is enabled by default on clean install`() {
        val preferences = SourcePreferences(InMemoryPreferenceStore())

        assertTrue(preferences.enableSeriesBlacklist().get())
    }

    @Test
    fun `blacklist normalization removes punctuation and accents`() {
        assertEquals("frierenbeyondjourneysend", "Frieren: Beyond Journey’s End".toBlacklistNormalizedTitle())
        assertEquals("oshinoko", "Oshi no Ko".toBlacklistNormalizedTitle())
        assertEquals("pokemon", "Pokémon".toBlacklistNormalizedTitle())
    }

    @Test
    fun `add blacklist entry stores unique normalized entries with added time`() {
        val preferences = SourcePreferences(InMemoryPreferenceStore())

        assertTrue(preferences.addBlacklistedSeries("Oshi no Ko"))
        assertFalse(preferences.addBlacklistedSeries("oshi-no,ko"))
        assertTrue(preferences.addBlacklistedSeries("Frieren: Beyond Journey's End"))

        val entries = preferences.blacklistedSeries().get()
        assertEquals(listOf("Oshi no Ko", "Frieren: Beyond Journey's End"), entries.map { it.originalTitle })
        assertEquals(listOf("oshinoko", "frierenbeyondjourneysend"), entries.map { it.normalizedTitle })
        assertTrue(entries.all { it.addedAt > 0L })
    }

    @Test
    fun `legacy blacklist entries are backfilled with added timestamps`() {
        val store = InMemoryPreferenceStore()
        val preferences = SourcePreferences(store)
        store.getString("series_blacklist", "").set(
            """
            [
                {"originalTitle":"A Legacy","normalizedTitle":"alegacy"},
                {"originalTitle":"B Legacy","normalizedTitle":"blegacy"}
            ]
            """.trimIndent(),
        )

        val entries = preferences.blacklistedSeries().get()
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.addedAt > 0L })
    }

    @Test
    fun `blacklist serializer includes added timestamp`() {
        val preferences = SourcePreferences(InMemoryPreferenceStore())
        assertTrue(preferences.addBlacklistedSeries("Dandadan"))
        val json = Json.encodeToString(ListSerializer(BlacklistedSeriesEntry.serializer()), preferences.blacklistedSeries().get())
        assertTrue(json.contains("\"addedAt\":"))
    }
}
