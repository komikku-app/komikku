package app.komikku.desktop.compat

import android.content.SharedPreferences
import tachiyomi.core.common.preference.DesktopPreferenceStore
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [SharedPreferences] backed by a [DesktopPreferenceStore]; every `commit()` / `apply()` is one
 * batch [DesktopPreferenceStore.edit] (one file write). Obtain instances through
 * [DesktopPreferenceStores] so each file has a single store.
 *
 * Differences from Android: `apply()` writes synchronously, and listeners are called on the
 * committing thread, only for changes made through this object.
 */
class DesktopSharedPreferences(
    val store: DesktopPreferenceStore,
) : SharedPreferences {

    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    override val all: Map<String, *>
        get() = store.getAll().mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }

    override fun getString(key: String, defValue: String?): String? = store.getAll()[key] as String? ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (store.getAll()[key] as Set<String>?)?.toSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = store.getAll()[key] as Int? ?: defValue

    override fun getLong(key: String, defValue: Long): Long = store.getAll()[key] as Long? ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = store.getAll()[key] as Float? ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = store.getAll()[key] as Boolean? ?: defValue

    override fun contains(key: String): Boolean = store.getAll().containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners.addIfAbsent(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners.remove(listener)
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val lock = Any()
        private val changes = LinkedHashMap<String, Any?>()
        private var clear = false

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            synchronized(lock) { changes[key] = value }
            return this
        }

        override fun putString(key: String, value: String?) = put(key, value)

        override fun putStringSet(key: String, values: Set<String>?) = put(key, values?.toSet())

        override fun putInt(key: String, value: Int) = put(key, value)

        override fun putLong(key: String, value: Long) = put(key, value)

        override fun putFloat(key: String, value: Float) = put(key, value)

        override fun putBoolean(key: String, value: Boolean) = put(key, value)

        override fun remove(key: String) = put(key, null)

        override fun clear(): SharedPreferences.Editor {
            synchronized(lock) { clear = true }
            return this
        }

        override fun commit(): Boolean {
            val (doClear, pending) = synchronized(lock) {
                (clear to changes.toMap()).also {
                    clear = false
                    changes.clear()
                }
            }
            val before = store.getAll()
            // Like Android: `clear()` applies first, then every put / remove of this editor.
            store.edit {
                if (doClear) clear()
                pending.forEach { (key, value) -> if (value == null) remove(key) else put(key, value) }
            }
            notifyListeners(doClear, before, store.getAll())
            return true
        }

        override fun apply() {
            commit()
        }
    }

    private fun notifyListeners(cleared: Boolean, before: Map<String, *>, after: Map<String, *>) {
        if (listeners.isEmpty()) return
        if (cleared) listeners.forEach { it.onSharedPreferenceChanged(this, null) }
        (before.keys + after.keys)
            .filter { before[it] != after[it] }
            .forEach { key -> listeners.forEach { it.onSharedPreferenceChanged(this, key) } }
    }
}
