package eu.kanade.tachiyomi.data.backup.create

internal object BackupCreateFlags {
    const val BACKUP_CATEGORY = 0x1
    const val BACKUP_CHAPTER = 0x2
    const val BACKUP_HISTORY = 0x4
    const val BACKUP_TRACK = 0x8
    const val BACKUP_APP_PREFS = 0x10
    const val BACKUP_SOURCE_PREFS = 0x20

    // SY -->
    const val BACKUP_CUSTOM_INFO = 0x40
    const val BACKUP_READ_MANGA = 0x80
    // SY <--

    const val AutomaticDefaults = BACKUP_CATEGORY or
        BACKUP_CHAPTER or
        BACKUP_HISTORY or
        BACKUP_TRACK or
        BACKUP_APP_PREFS or
        BACKUP_SOURCE_PREFS /* SY --> */ or
        BACKUP_CUSTOM_INFO or
        BACKUP_READ_MANGA
    // SY <--
}
