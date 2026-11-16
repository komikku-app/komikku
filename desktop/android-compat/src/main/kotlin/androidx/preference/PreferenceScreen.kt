package androidx.preference

import android.content.Context

/**
 * android-compat: AndroidX `PreferenceScreen`, the root of a preference hierarchy. Created through
 * [PreferenceManager.createPreferenceScreen] (which attaches it, so its preferences persist); this
 * is the type `ConfigurableSource.setupPreferenceScreen` receives on desktop.
 */
class PreferenceScreen internal constructor(context: Context) : PreferenceGroup(context) {

    override fun isOnSameScreenAsChildren(): Boolean = false

    // A screen shown as the root is never "clicked"; nested screens are rendered as sub-pages.
    override fun onClick() {}
}
