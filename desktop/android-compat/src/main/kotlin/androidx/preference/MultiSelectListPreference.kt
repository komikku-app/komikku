package androidx.preference

import android.content.Context

/**
 * android-compat: AndroidX `MultiSelectListPreference`, a set of values chosen from [entries]
 * (labels) / [entryValues] (stored strings).
 */
open class MultiSelectListPreference(context: Context) : DialogPreference(context) {

    private val selected = linkedSetOf<String>()

    open var entries: Array<out CharSequence>? = null
    open var entryValues: Array<out CharSequence>? = null

    open var values: Set<String>
        get() = selected
        set(newValues) {
            selected.clear()
            selected.addAll(newValues)
            persistStringSet(newValues)
            notifyChanged()
        }

    open fun findIndexOfValue(value: String?): Int {
        if (value == null) return -1
        return entryValues?.indexOfLast { it.toString() == value } ?: -1
    }

    /** One flag per entry, `true` when its value is selected. */
    protected open fun getSelectedItems(): BooleanArray {
        val all = entryValues ?: return BooleanArray(0)
        return BooleanArray(all.size) { all[it].toString() in selected }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onSetInitialValue(defaultValue: Any?) {
        values = getPersistedStringSet(defaultValue as Set<String>?) ?: emptySet()
    }
}
