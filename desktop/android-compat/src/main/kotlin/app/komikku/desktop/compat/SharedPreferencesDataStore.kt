package app.komikku.desktop.compat

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore

/**
 * The storage `androidx.preference.Preference` uses when no [PreferenceDataStore] is set: the
 * [PreferenceManager][androidx.preference.PreferenceManager]'s shared preferences, one `apply()`
 * per write.
 */
internal class SharedPreferencesDataStore(val preferences: SharedPreferences) : PreferenceDataStore() {

    override fun putString(key: String, value: String?) = preferences.edit().putString(key, value).apply()

    override fun putStringSet(key: String, values: Set<String>?) = preferences.edit().putStringSet(key, values).apply()

    override fun putInt(key: String, value: Int) = preferences.edit().putInt(key, value).apply()

    override fun putLong(key: String, value: Long) = preferences.edit().putLong(key, value).apply()

    override fun putFloat(key: String, value: Float) = preferences.edit().putFloat(key, value).apply()

    override fun putBoolean(key: String, value: Boolean) = preferences.edit().putBoolean(key, value).apply()

    override fun getString(key: String, defValue: String?): String? = preferences.getString(key, defValue)

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        preferences.getStringSet(key, defValues)

    override fun getInt(key: String, defValue: Int): Int = preferences.getInt(key, defValue)

    override fun getLong(key: String, defValue: Long): Long = preferences.getLong(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float = preferences.getFloat(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = preferences.getBoolean(key, defValue)
}
