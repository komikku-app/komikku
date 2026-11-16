@file:JvmName("SharedPreferencesKt")

package androidx.core.content

import android.content.SharedPreferences

/**
 * android-compat: core-ktx `SharedPreferences.edit { }` (8.1, borrowed `DownloadStore` /
 * `DownloadPendingDeleter`). Same semantics: one editor, `commit()` or `apply()` at the end.
 */
inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    action(editor)
    if (commit) {
        editor.commit()
    } else {
        editor.apply()
    }
}
