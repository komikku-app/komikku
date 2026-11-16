package androidx.preference

import android.content.Context
import app.komikku.desktop.compat.NOT_SET

/**
 * android-compat: AndroidX `ListPreference`, one value chosen from [entries] (labels) /
 * [entryValues] (stored strings).
 *
 * Like AndroidX, a [summary] containing `%s` is formatted with the current entry when no
 * [summaryProvider] is set.
 */
open class ListPreference(context: Context) : DialogPreference(context) {

    /** Summary = current entry, or "Not set" when nothing is selected. */
    class SimpleSummaryProvider private constructor() : SummaryProvider<ListPreference> {
        override fun provideSummary(preference: ListPreference): CharSequence? =
            preference.entry.takeUnless { it.isNullOrEmpty() } ?: NOT_SET

        companion object {
            private val singleton by lazy { SimpleSummaryProvider() }

            @JvmStatic
            fun getInstance(): SimpleSummaryProvider = singleton
        }
    }

    private var currentValue: String? = null
    private var valueSet = false

    open var entries: Array<out CharSequence>? = null
    open var entryValues: Array<out CharSequence>? = null

    open var value: String?
        get() = currentValue
        set(newValue) {
            val changed = newValue != currentValue
            if (changed || !valueSet) {
                currentValue = newValue
                valueSet = true
                persistString(newValue)
                if (changed) notifyChanged()
            }
        }

    /** Label of the current value, or `null`. */
    open val entry: CharSequence?
        get() = entries?.getOrNull(findIndexOfValue(currentValue))

    override var summary: CharSequence?
        get() {
            if (summaryProvider != null) return super.summary
            val pattern = super.summary ?: return null
            return if (pattern.contains("%s")) String.format(pattern.toString(), entry ?: "") else pattern
        }
        set(value) {
            super.summary = value
        }

    open fun findIndexOfValue(value: String?): Int {
        if (value == null) return -1
        return entryValues?.indexOfLast { it.toString() == value } ?: -1
    }

    open fun setValueIndex(index: Int) {
        entryValues?.let { value = it[index].toString() }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedString(defaultValue as String?)
    }
}
