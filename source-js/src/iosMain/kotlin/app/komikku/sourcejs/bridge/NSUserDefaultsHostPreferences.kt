package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.model.JsPreference
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/**
 * The script's `SharedPreferences` (contract 4.6) on iOS: one [NSUserDefaults] suite per source, typed like the JVM's
 * `SharedPreferencesHostPreferences` so a settings screen reads the values with their natural types: check box /
 * switch → `Bool` (`setBool`), list / edit text → `NSString`, multi-select → `NSArray` of strings.
 *
 * The suite is named like the Android / desktop preference file of the source, `source_<komikkuId>`
 * (`JsSourceIds.preferencesName`), see [IosJsSourceRuntime][app.komikku.sourcejs.IosJsSourceRuntime].
 *
 * [declared] is the source's current `getSourcePreferences()` (empty while unknown). A stored value whose type does not
 * fit the declared preference reads as missing, so the script gets the declared default; a string list is returned in
 * the declared `entryValues` order, other values after them in natural order (the JVM store's set semantics).
 *
 * The free string store behind `getString` / `setString` lives in the same suite under keys prefixed with
 * [STRING_KEY_PREFIX] (the JVM prefix), so it cannot clash with declared keys.
 */
class NSUserDefaultsHostPreferences(
    private val defaults: NSUserDefaults,
    private val declared: () -> List<JsPreference> = { emptyList() },
) : HostPreferences {

    override fun getValue(key: String): JsPreferenceValue? {
        val stored = defaults.objectForKey(key) ?: return null
        val declaration = declared().firstOrNull { it.key == key }
        return when (stored) {
            // Only `setBool` writes numbers here.
            is NSNumber -> if (declaration == null || declaration is JsPreference.CheckBox || declaration is JsPreference.Switch) {
                JsPreferenceValue.Bool(stored.boolValue)
            } else {
                null
            }
            is String -> if (declaration == null || declaration is JsPreference.List || declaration is JsPreference.EditText) {
                JsPreferenceValue.Text(stored)
            } else {
                null
            }
            is List<*> -> if (declaration == null || declaration is JsPreference.MultiSelect) {
                val values = stored.filterIsInstance<String>().distinct()
                val order = declaration?.entryValues.orEmpty()
                JsPreferenceValue.TextSet(order.filter { it in values } + values.filterNot { it in order }.sorted())
            } else {
                null
            }
            else -> null
        }
    }

    override fun setValue(key: String, value: JsPreferenceValue) {
        when (value) {
            is JsPreferenceValue.Bool -> defaults.setBool(value.value, forKey = key)
            is JsPreferenceValue.Text -> defaults.setObject(value.value, forKey = key)
            is JsPreferenceValue.TextSet -> defaults.setObject(value.values.distinct(), forKey = key)
        }
    }

    override fun getString(key: String): String? = defaults.objectForKey(STRING_KEY_PREFIX + key) as? String

    override fun setString(key: String, value: String) {
        defaults.setObject(value, forKey = STRING_KEY_PREFIX + key)
    }

    companion object {
        /** Prefix of the keys of the free string store, the same as the JVM store's. */
        const val STRING_KEY_PREFIX = "mangayomi_str:"
    }
}

/**
 * A [JsKeyValueStore] on [NSUserDefaults] (by default the app's standard defaults), for [KeyValueHostPreferences]
 * (tagged strings, all sources in one store namespaced by source id). Keys get [keyPrefix]. [IosJsSourceRuntime][app.komikku.sourcejs.IosJsSourceRuntime]
 * uses the typed per-source [NSUserDefaultsHostPreferences] instead; this one is for app-level string settings.
 */
class NSUserDefaultsKeyValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val keyPrefix: String = "",
) : JsKeyValueStore {
    override fun get(key: String): String? = defaults.objectForKey(keyPrefix + key) as? String

    override fun put(key: String, value: String) {
        defaults.setObject(value, forKey = keyPrefix + key)
    }
}
