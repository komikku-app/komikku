package androidx.preference

import android.content.Context

/** android-compat: AndroidX `SwitchPreferenceCompat`. */
open class SwitchPreferenceCompat(context: Context) : TwoStatePreference(context) {

    open var switchTextOn: CharSequence? = null
    open var switchTextOff: CharSequence? = null
}
