package tachiyomi.domain.storage.service

import android.content.Context
import tachiyomi.core.common.preference.Preference

/** KMK: desktop has no storage permission model; the user just picks a folder. */
internal actual fun allowAccessStoragePlatform(
    context: Context,
    storageDirPref: Preference<String>,
    pickStorageLocation: () -> Unit,
) {
    pickStorageLocation()
}
