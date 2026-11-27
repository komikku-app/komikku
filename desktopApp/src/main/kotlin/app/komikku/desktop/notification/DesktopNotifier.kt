package app.komikku.desktop.notification

/**
 * P17: one system notification of the desktop app (what Android's notifiers post: new chapters, update errors, downloader
 * errors / warnings, backup and restore results, an app update). [channel] is Android's notification channel it stands
 * for; [text] may hold several lines.
 */
data class DesktopNotification(
    val channel: Channel,
    val title: String,
    val text: String,
    val level: Level = Level.Info,
) {
    enum class Channel { NewChapters, LibraryErrors, Downloader, Backup, Restore, AppUpdate }

    enum class Level { Info, Warning, Error }
}

/**
 * P17: where the desktop's notifications go: the system tray ([DesktopTray]) in the app, a recording fake in tests. Kept
 * small so the event → notification rules ([DesktopNotifications]) are tested without a desktop.
 */
interface DesktopNotifier {

    /**
     * Shows [notification]; `false` when it could not be shown (system notifications off, no tray icon: off,
     * unsupported or headless). `true` means handed to the system: an asynchronous backend ([MacOsNotifier]) cannot
     * tell whether the system then displayed it.
     */
    fun show(notification: DesktopNotification): Boolean

    /** A short status of running work (the tray icon's tooltip: the library update's progress); `null` when idle. */
    fun status(text: String?) {}
}
