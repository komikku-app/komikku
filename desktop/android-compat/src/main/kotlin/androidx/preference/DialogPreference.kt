package androidx.preference

import android.content.Context

/**
 * android-compat: AndroidX `DialogPreference`. The desktop renderer opens its own dialog on click,
 * using these texts (the title falls back to [title], as in the app's Android source screen).
 */
abstract class DialogPreference(context: Context) : Preference(context) {

    open var dialogTitle: CharSequence? = null
    open var dialogMessage: CharSequence? = null
    open var positiveButtonText: CharSequence? = null
    open var negativeButtonText: CharSequence? = null
    open var dialogLayoutResource: Int = 0
}
