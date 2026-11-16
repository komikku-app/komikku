package androidx.preference

import android.content.Context

/** android-compat: AndroidX `SwitchPreference`. */
open class SwitchPreference(context: Context) : TwoStatePreference(context) {

    open var switchTextOn: CharSequence? = null
    open var switchTextOff: CharSequence? = null
}
