package tachiyomi.core.common.preference

import android.content.Context
import android.content.SharedPreferences
import app.komikku.desktop.compat.DesktopSharedPreferences

/**
 * KMK: desktop counterpart of `AndroidPreferenceStore(context, sharedPreferences)` (core:common
 * androidMain) for the borrowed `PreferenceRestorer` (source preferences): the [PreferenceStore] behind a
 * compat [SharedPreferences] (`Context.getSharedPreferences` / `sourcePreferences(key)`), so values
 * written here are seen by extensions' `SharedPreferences` and vice versa.
 */
@Suppress("FunctionName", "UNUSED_PARAMETER")
fun AndroidPreferenceStore(context: Context, sharedPreferences: SharedPreferences): PreferenceStore =
    (sharedPreferences as DesktopSharedPreferences).store
