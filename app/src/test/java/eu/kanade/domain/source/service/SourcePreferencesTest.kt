package eu.kanade.domain.source.service

import eu.kanade.tachiyomi.util.lang.toBlacklistNormalizedTitle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class SourcePreferencesTest {

    @Test
    fun `blacklist normalization removes punctuation and accents`() {
        assertEquals("frierenbeyondjourneysend", "Frieren: Beyond Journey’s End".toBlacklistNormalizedTitle())
        assertEquals("oshinoko", "Oshi no Ko".toBlacklistNormalizedTitle())
        assertEquals("pokemon", "Pokémon".toBlacklistNormalizedTitle())
    }

    @Test
    fun `add blacklist entry stores sorted unique normalized entries`() {
        val preferences = SourcePreferences(InMemoryPreferenceStore())

        assertTrue(preferences.addBlacklistedSeries("Oshi no Ko"))
        assertFalse(preferences.addBlacklistedSeries("oshi-no,ko"))
        assertTrue(preferences.addBlacklistedSeries("Frieren: Beyond Journey's End"))

        val entries = preferences.blacklistedSeries().get()
        assertEquals(listOf("Frieren: Beyond Journey's End", "Oshi no Ko"), entries.map { it.originalTitle })
        assertEquals(listOf("frierenbeyondjourneysend", "oshinoko"), entries.map { it.normalizedTitle })
    }
}
