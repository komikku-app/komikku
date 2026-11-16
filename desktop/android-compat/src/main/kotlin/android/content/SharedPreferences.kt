package android.content

/**
 * android-compat: `android.content.SharedPreferences`. Desktop instances come from
 * `Context.getSharedPreferences` and are backed by the desktop preference store
 * (`app.komikku.desktop.compat.DesktopSharedPreferences`).
 */
interface SharedPreferences {

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    }

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }

    val all: Map<String, *>

    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
}
