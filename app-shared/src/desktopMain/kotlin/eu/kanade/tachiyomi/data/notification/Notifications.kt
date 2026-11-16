package eu.kanade.tachiyomi.data.notification

/**
 * KMK: desktop counterpart of the Android `Notifications` (ids only, same values) for borrowed files
 * that pass notification ids around (backup restore progress).
 */
object Notifications {
    const val ID_BACKUP_PROGRESS = -501
    const val ID_BACKUP_COMPLETE = -502
    const val ID_RESTORE_PROGRESS = -503
    const val ID_RESTORE_COMPLETE = -504
}
