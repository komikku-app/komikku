package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.model.JsPreference
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [NSUserDefaultsHostPreferences] and [NSUserDefaultsKeyValueStore] on a throwaway suite. */
class NSUserDefaultsHostPreferencesTest {

    private val suite = "app.komikku.sourcejs.test.${Random.nextLong().toULong()}"
    private val defaults = NSUserDefaults(suiteName = suite)

    @AfterTest
    fun removeSuite() {
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suite)
    }

    @Test
    fun valuesAreStoredWithNativeTypes() {
        val preferences = NSUserDefaultsHostPreferences(defaults)
        preferences.setValue("hd", JsPreferenceValue.Bool(true))
        preferences.setValue("lang", JsPreferenceValue.Text("fr"))
        preferences.setValue("langs", JsPreferenceValue.TextSet(listOf("b", "a", "b")))

        assertTrue(defaults.objectForKey("hd") is NSNumber)
        assertTrue(defaults.boolForKey("hd"))
        assertEquals("fr", defaults.stringForKey("lang"))
        assertEquals(listOf("b", "a"), defaults.stringArrayForKey("langs"))

        // Without declarations every stored type reads back; a list is in natural order.
        assertEquals(JsPreferenceValue.Bool(true), preferences.getValue("hd"))
        assertEquals(JsPreferenceValue.Text("fr"), preferences.getValue("lang"))
        assertEquals(JsPreferenceValue.TextSet(listOf("a", "b")), preferences.getValue("langs"))
        assertNull(preferences.getValue("missing"))
    }

    @Test
    fun declarationsTypeTheStoredValues() {
        defaults.setBool(true, forKey = "lang")
        defaults.setObject("yes", forKey = "hd")
        defaults.setObject(listOf("x", "b", "a"), forKey = "langs")
        val declared = listOf(
            JsPreference.List(key = "lang", title = "", summary = "", valueIndex = 0, entries = listOf("A"), entryValues = listOf("a")),
            JsPreference.Switch(key = "hd", title = "", summary = "", value = true),
            JsPreference.MultiSelect(key = "langs", title = "", summary = "", entries = listOf("B", "A"), entryValues = listOf("b", "a"), values = emptyList()),
        )
        val preferences = NSUserDefaultsHostPreferences(defaults) { declared }
        // A value of another type than declared reads as missing (the script then gets the declared default).
        assertNull(preferences.getValue("lang"))
        assertNull(preferences.getValue("hd"))
        // A list comes back in the declared order, unknown values after it.
        assertEquals(JsPreferenceValue.TextSet(listOf("b", "a", "x")), preferences.getValue("langs"))
    }

    @Test
    fun theFreeStringStoreIsSeparate() {
        val preferences = NSUserDefaultsHostPreferences(defaults)
        assertNull(preferences.getString("token"))
        preferences.setString("token", "abc")
        assertEquals("abc", preferences.getString("token"))
        assertNull(preferences.getValue("token"))
        assertEquals("abc", defaults.stringForKey(NSUserDefaultsHostPreferences.STRING_KEY_PREFIX + "token"))
        // Visible to a second instance on the same suite.
        assertEquals("abc", NSUserDefaultsHostPreferences(NSUserDefaults(suiteName = suite)).getString("token"))
    }

    @Test
    fun keyValueStoreUsesItsPrefix() {
        val store = NSUserDefaultsKeyValueStore(defaults, keyPrefix = "kv.")
        assertNull(store.get("a"))
        store.put("a", "1")
        assertEquals("1", store.get("a"))
        assertEquals("1", defaults.stringForKey("kv.a"))
        val tagged = KeyValueHostPreferences(store, sourceId = 7)
        tagged.setValue("hd", JsPreferenceValue.Bool(false))
        assertEquals(JsPreferenceValue.Bool(false), tagged.getValue("hd"))
    }
}
