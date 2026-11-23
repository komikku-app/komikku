package app.komikku.sourcejs.mihon

import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.komikku.sourcejs.bridge.JsPreferenceValue
import app.komikku.sourcejs.model.JsPreference
import eu.kanade.tachiyomi.source.PreferenceScreen

/**
 * A source's declared preferences (contract 5.2) as AndroidX preferences on Android and as the
 * `:desktop:android-compat` preference model on desktop (same names; this file is compiled for each target).
 *
 * The screen persists to the source's preference file, so the values the user picks are what the script's
 * `SharedPreferences.get(key)` reads ([SharedPreferencesHostPreferences] uses the same keys and types). Preferences
 * with a blank or repeated key are skipped; entry lists are cut to the shorter of `entries` / `entryValues`, since
 * AndroidX indexes one by the other.
 */
object JsPreferenceScreens {

    fun setup(screen: PreferenceScreen, preferences: List<JsPreference>) {
        val context = screen.context
        val seen = HashSet<String>()
        preferences.forEach { declared ->
            if (declared.key.isBlank() || !seen.add(declared.key)) return@forEach
            val preference: Preference = when (declared) {
                is JsPreference.CheckBox -> CheckBoxPreference(context).apply { setDefaultValue(declared.value) }
                is JsPreference.Switch -> SwitchPreferenceCompat(context).apply { setDefaultValue(declared.value) }
                is JsPreference.List -> ListPreference(context).apply {
                    val (labels, values) = entries(declared.entries, declared.entryValues)
                    entries = labels
                    entryValues = values
                    setDefaultValue((declared.defaultValue as JsPreferenceValue.Text).value)
                }
                is JsPreference.MultiSelect -> MultiSelectListPreference(context).apply {
                    val (labels, values) = entries(declared.entries, declared.entryValues)
                    entries = labels
                    entryValues = values
                    setDefaultValue(declared.values.toSet())
                }
                is JsPreference.EditText -> EditTextPreference(context).apply {
                    dialogTitle = declared.dialogTitle.ifBlank { declared.title }
                    if (declared.dialogMessage.isNotBlank()) dialogMessage = declared.dialogMessage
                    setDefaultValue(declared.value)
                }
            }
            preference.key = declared.key
            preference.title = declared.title
            preference.summary = when {
                declared.summary.isNotBlank() -> declared.summary
                // Show the chosen entry, like Mangayomi's list tiles.
                declared is JsPreference.List -> "%s"
                else -> null
            }
            screen.addPreference(preference)
        }
    }

    private fun entries(labels: List<String>, values: List<String>): Pair<Array<String>, Array<String>> {
        val size = minOf(labels.size, values.size)
        return labels.take(size).toTypedArray() to values.take(size).toTypedArray()
    }
}
