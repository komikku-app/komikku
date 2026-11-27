package eu.kanade.domain.source.service

import eu.kanade.domain.source.model.BlacklistedSeriesEntry
import eu.kanade.tachiyomi.util.lang.toBlacklistNormalizedTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class SourcePreferencesTest {

    @Test
    fun `series blacklist is enabled by default on clean install`() {
        val preferences = SourcePreferences(StringBackedPreferenceStore())

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
        val preferences = SourcePreferences(StringBackedPreferenceStore())

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
        val store = StringBackedPreferenceStore()
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
        val preferences = SourcePreferences(StringBackedPreferenceStore())
        assertTrue(preferences.addBlacklistedSeries("Dandadan"))
        val json = Json.encodeToString(ListSerializer(BlacklistedSeriesEntry.serializer()), preferences.blacklistedSeries().get())
        assertTrue(json.contains("\"addedAt\":"))
    }
}

/**
 * [InMemoryPreferenceStore] doesn't persist writes or run serializers, so string-backed
 * preferences are stored here as they would be in SharedPreferences.
 */
private class StringBackedPreferenceStore(
    delegate: PreferenceStore = InMemoryPreferenceStore(),
) : PreferenceStore by delegate {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String, defaultValue: String): Preference<String> =
        getObjectFromString(key, defaultValue, { it }, { it })

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = object : Preference<T> {
        override fun key(): String = key

        override fun get(): T = values[key]?.let(deserializer) ?: defaultValue

        override fun set(value: T) {
            values[key] = serializer(value)
        }

        override fun isSet(): Boolean = key in values

        override fun delete() {
            values.remove(key)
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = flowOf(get())

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
    }
}
