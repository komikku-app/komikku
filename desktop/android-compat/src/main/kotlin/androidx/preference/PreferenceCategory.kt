package androidx.preference

import android.content.Context

/**
 * android-compat: AndroidX `PreferenceCategory`, a titled section of a screen. Like AndroidX it
 * reports itself as disabled (it is not clickable) while its children stay usable.
 */
open class PreferenceCategory(context: Context) : PreferenceGroup(context) {

    override var isEnabled: Boolean
        get() = false
        set(value) {
            super.isEnabled = value
        }

    override fun shouldDisableDependents(): Boolean = !super.isEnabled

    init {
        isSelectable = false
        isPersistent = false
    }
}
