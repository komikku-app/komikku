package androidx.preference

import android.content.Context

/**
 * android-compat: AndroidX `TwoStatePreference`, the base of checkboxes and switches. A click
 * ([performClick]) toggles [isChecked] after the change listener accepted the new value.
 */
abstract class TwoStatePreference(context: Context) : Preference(context) {

    private var checkedState = false
    private var checkedSet = false

    open var isChecked: Boolean
        get() = checkedState
        set(value) {
            val changed = checkedState != value
            if (changed || !checkedSet) {
                checkedState = value
                checkedSet = true
                persistBoolean(value)
                if (changed) {
                    notifyDependencyChange(shouldDisableDependents())
                    notifyChanged()
                }
            }
        }

    /** Summary shown while checked (falls back to [summary]). */
    open var summaryOn: CharSequence? = null

    /** Summary shown while unchecked (falls back to [summary]). */
    open var summaryOff: CharSequence? = null

    /** `true` if dependents are disabled while checked instead of while unchecked. */
    open var disableDependentsState: Boolean = false

    override fun onClick() {
        val newValue = !isChecked
        if (callChangeListener(newValue)) isChecked = newValue
    }

    override fun shouldDisableDependents(): Boolean =
        (if (disableDependentsState) checkedState else !checkedState) || super.shouldDisableDependents()

    override fun onSetInitialValue(defaultValue: Any?) {
        isChecked = getPersistedBoolean(defaultValue as Boolean? ?: false)
    }
}
