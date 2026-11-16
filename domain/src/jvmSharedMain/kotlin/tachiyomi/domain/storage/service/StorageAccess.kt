package tachiyomi.domain.storage.service

import android.content.Context
import tachiyomi.core.common.preference.Preference

/**
 * KMK: platform part of [StorageManager.Companion.allowAccessStorage].
 *
 * Android: opens the document tree picker when available, otherwise requests the All Files Access
 * permission (or falls back to app-specific storage). Desktop: always the folder picker.
 */
internal expect fun allowAccessStoragePlatform(
    context: Context,
    storageDirPref: Preference<String>,
    pickStorageLocation: () -> Unit,
)
