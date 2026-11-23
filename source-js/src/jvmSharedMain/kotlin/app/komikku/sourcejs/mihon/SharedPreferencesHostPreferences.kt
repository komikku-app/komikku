package app.komikku.sourcejs.mihon

import android.content.SharedPreferences
import app.komikku.sourcejs.bridge.HostPreferences
import app.komikku.sourcejs.bridge.JsPreferenceValue
import app.komikku.sourcejs.model.JsPreference

/**
 * The script's `SharedPreferences` (contract 4.6) over the source's Android-style [SharedPreferences] file
 * (`source_<komikkuId>`, [JsSourceIds.preferencesName]), the file the app's source settings screen
 * ([JsPreferenceScreens]) writes and backups include. Values are stored with Android types, so both sides read them
 * directly: check box / switch → `Boolean`, list / edit text → `String`, multi-select → `Set<String>`.
 *
 * [declared] is the source's current `getSourcePreferences()` (from the adapter's metadata; empty before it is
 * known). A stored value whose type does not fit the declared preference reads as missing, so the script gets the
 * declared default; a string set is returned in the declared `entryValues` order (sets have no order), other values
 * after them in natural order.
 *
 * The free string store behind `getString` / `setString` lives in the same file under keys prefixed with
 * [STRING_KEY_PREFIX], so it cannot clash with declared keys and the settings screen never shows it.
 */
class SharedPreferencesHostPreferences(
    private val preferences: SharedPreferences,
    private val declared: () -> List<JsPreference> = { emptyList() },
) : HostPreferences {

    override fun getValue(key: String): JsPreferenceValue? {
        val stored = preferences.all[key] ?: return null
        val declaration = declared().firstOrNull { it.key == key }
        if (!fits(stored, declaration)) return null
        return when (stored) {
            is Boolean -> JsPreferenceValue.Bool(stored)
            is String -> JsPreferenceValue.Text(stored)
            is Set<*> -> {
                val values = stored.filterIsInstance<String>()
                val order = (declaration as JsPreference.MultiSelect?)?.entryValues.orEmpty()
                JsPreferenceValue.TextSet(order.filter { it in values } + values.filterNot { it in order }.sorted())
            }
            else -> null
        }
    }

    override fun setValue(key: String, value: JsPreferenceValue) {
        val editor = preferences.edit()
        when (value) {
            is JsPreferenceValue.Bool -> editor.putBoolean(key, value.value)
            is JsPreferenceValue.Text -> editor.putString(key, value.value)
            is JsPreferenceValue.TextSet -> editor.putStringSet(key, LinkedHashSet(value.values))
        }
        editor.apply()
    }

    override fun getString(key: String): String? = preferences.all[STRING_KEY_PREFIX + key] as? String

    override fun setString(key: String, value: String) {
        preferences.edit().putString(STRING_KEY_PREFIX + key, value).apply()
    }

    companion object {
        /** Prefix of the keys of the free string store (`SharedPreferences.getString` / `setString`). */
        const val STRING_KEY_PREFIX = "mangayomi_str:"

        /**
         * Whether the [stored] value has the Android type of [declaration] (check box / switch `Boolean`, list / edit
         * text `String`, multi-select `Set`); without a declaration every type the script can store fits.
         */
        fun fits(stored: Any, declaration: JsPreference?): Boolean = when (stored) {
            is Boolean -> declaration == null || declaration is JsPreference.CheckBox || declaration is JsPreference.Switch
            is String -> declaration == null || declaration is JsPreference.List || declaration is JsPreference.EditText
            is Set<*> -> declaration == null || declaration is JsPreference.MultiSelect
            else -> false
        }
    }
}
