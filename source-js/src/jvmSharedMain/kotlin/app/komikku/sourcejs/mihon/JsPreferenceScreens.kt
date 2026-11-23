package app.komikku.sourcejs.mihon

import android.content.SharedPreferences
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
 *
 * The widgets read their stored values with typed getters, so a stored value of another type than the declared
 * preference's (left by an earlier version of the source) would throw a `ClassCastException`: it is removed first, by
 * the rule the script's reads use ([SharedPreferencesHostPreferences.fits]), and the widget starts from the declared
 * default, which is also what the script read. A list's summary goes through a [Preference.SummaryProvider] (the
 * declared summary verbatim, or the current entry when blank): AndroidX runs `String.format` on a plain list summary,
 * which throws on a lone `%`.
 */
object JsPreferenceScreens {

    /**
     * Adds [preferences] to [screen]; [stored] is the file the screen persists to (the source's preference file),
     * cleaned of values of the wrong type before the widgets read it.
     */
    fun setup(screen: PreferenceScreen, preferences: List<JsPreference>, stored: SharedPreferences? = screen.sharedPreferences) {
        val context = screen.context
        val seen = HashSet<String>()
        val shown = preferences.filter { declared -> declared.key.isNotBlank() && seen.add(declared.key) }
        if (stored != null) removeMismatchedValues(stored, shown)
        shown.forEach { declared ->
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
            if (preference is ListPreference) {
                // Never a plain summary (formatted by AndroidX); blank shows the chosen entry, like Mangayomi's list tiles.
                val summary = declared.summary
                preference.summaryProvider = Preference.SummaryProvider<ListPreference> { list ->
                    summary.ifBlank { list.entry?.toString().orEmpty() }
                }
            } else if (declared.summary.isNotBlank()) {
                preference.summary = declared.summary
            }
            screen.addPreference(preference)
        }
    }

    private fun removeMismatchedValues(stored: SharedPreferences, preferences: List<JsPreference>) {
        val values = stored.all
        val mismatched = preferences.filter { declared ->
            val value = values[declared.key]
            value != null && !SharedPreferencesHostPreferences.fits(value, declared)
        }
        if (mismatched.isEmpty()) return
        stored.edit().apply { mismatched.forEach { remove(it.key) } }.apply()
    }

    private fun entries(labels: List<String>, values: List<String>): Pair<Array<String>, Array<String>> {
        val size = minOf(labels.size, values.size)
        return labels.take(size).toTypedArray() to values.take(size).toTypedArray()
    }
}
