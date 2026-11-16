package androidx.preference

/**
 * android-compat: AndroidX `PreferenceDataStore`. Replaces shared preferences as the storage of a
 * preference (or of every preference of a [PreferenceManager]). Getters return the default and
 * setters throw unless overridden, as in AndroidX.
 */
abstract class PreferenceDataStore {

    open fun putString(key: String, value: String?): Unit = notImplemented()

    open fun putStringSet(key: String, values: Set<String>?): Unit = notImplemented()

    open fun putInt(key: String, value: Int): Unit = notImplemented()

    open fun putLong(key: String, value: Long): Unit = notImplemented()

    open fun putFloat(key: String, value: Float): Unit = notImplemented()

    open fun putBoolean(key: String, value: Boolean): Unit = notImplemented()

    open fun getString(key: String, defValue: String?): String? = defValue

    open fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues

    open fun getInt(key: String, defValue: Int): Int = defValue

    open fun getLong(key: String, defValue: Long): Long = defValue

    open fun getFloat(key: String, defValue: Float): Float = defValue

    open fun getBoolean(key: String, defValue: Boolean): Boolean = defValue

    private fun notImplemented(): Nothing = throw UnsupportedOperationException("Not implemented on this data store")
}
