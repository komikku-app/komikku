package android.os

/**
 * android-compat: `android.os.Bundle`, a string-keyed map of values. Extensions get it in activity callbacks
 * ([android.app.Application.ActivityLifecycleCallbacks]), which the desktop never calls; kept to plain storage.
 */
class Bundle() {

    private val values = LinkedHashMap<String, Any?>()

    constructor(other: Bundle) : this() {
        values.putAll(other.values)
    }

    fun size(): Int = values.size

    fun isEmpty(): Boolean = values.isEmpty()

    fun clear() = values.clear()

    fun containsKey(key: String?): Boolean = key != null && values.containsKey(key)

    fun remove(key: String?) {
        values.remove(key)
    }

    fun keySet(): Set<String> = values.keys

    fun putAll(bundle: Bundle) = values.putAll(bundle.values)

    fun putString(key: String?, value: String?) = put(key, value)

    fun getString(key: String?): String? = values[key] as? String

    fun getString(key: String?, defaultValue: String?): String? = getString(key) ?: defaultValue

    fun putInt(key: String?, value: Int) = put(key, value)

    fun getInt(key: String?): Int = getInt(key, 0)

    fun getInt(key: String?, defaultValue: Int): Int = values[key] as? Int ?: defaultValue

    fun putLong(key: String?, value: Long) = put(key, value)

    fun getLong(key: String?): Long = getLong(key, 0L)

    fun getLong(key: String?, defaultValue: Long): Long = values[key] as? Long ?: defaultValue

    fun putBoolean(key: String?, value: Boolean) = put(key, value)

    fun getBoolean(key: String?): Boolean = getBoolean(key, false)

    fun getBoolean(key: String?, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue

    private fun put(key: String?, value: Any?) {
        if (key != null) values[key] = value
    }

    override fun toString(): String = "Bundle[$values]"
}
