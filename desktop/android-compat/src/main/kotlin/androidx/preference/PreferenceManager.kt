package androidx.preference

import android.content.Context
import android.content.SharedPreferences
import tachiyomi.core.common.preference.DesktopPreferenceStore

/**
 * android-compat: AndroidX `PreferenceManager` (static helpers only). The default shared
 * preferences are the app's main desktop preference store
 * ([DesktopPreferenceStore.DEFAULT_STORE_NAME]), just as Android's default shared preferences
 * back the app's main `PreferenceStore`.
 */
class PreferenceManager private constructor() {

    companion object {
        @JvmStatic
        fun getDefaultSharedPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences(getDefaultSharedPreferencesName(context), Context.MODE_PRIVATE)

        @JvmStatic
        fun getDefaultSharedPreferencesName(@Suppress("UNUSED_PARAMETER") context: Context): String =
            DesktopPreferenceStore.DEFAULT_STORE_NAME
    }
}
