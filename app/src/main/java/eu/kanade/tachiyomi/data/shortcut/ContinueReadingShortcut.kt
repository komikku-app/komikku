package eu.kanade.tachiyomi.data.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.repository.HistoryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * A launcher shortcut (long-press on the app icon) that opens the chapter to read next, straight in the reader.
 *
 * It is opt-in: the title of what is being read is shown on the launcher, so it is only published when
 * [ReaderPreferences.continueReadingShortcut] is on.
 */
object ContinueReadingShortcut {

    const val SHORTCUT_ID = "continue_reading"
    private const val MAX_LABEL_LENGTH = 25

    /** Publishes, or removes when it is off or there is nothing left to read, the shortcut. */
    suspend fun update(
        context: Context,
        preferences: ReaderPreferences = Injekt.get(),
        historyRepository: HistoryRepository = Injekt.get(),
        getNextChapters: GetNextChapters = Injekt.get(),
        // The preference is not saved yet when the settings switch asks for an update
        enabled: Boolean = preferences.continueReadingShortcut().get(),
    ) {
        try {
            val appContext = context.applicationContext
            if (!enabled) {
                remove(appContext)
                return
            }

            val history = historyRepository.getLastHistory()
            val chapter = history?.let { getNextChapters.await(it.mangaId, onlyUnread = true).firstOrNull() }
            if (history == null || chapter == null) {
                remove(appContext)
                return
            }

            val intent = ReaderActivity.newIntent(appContext, history.mangaId, chapter.id)
                .setAction(Intent.ACTION_VIEW)
            val label = labelFor(history.title)
            val shortcut = ShortcutInfoCompat.Builder(appContext, SHORTCUT_ID)
                .setShortLabel(label)
                .setLongLabel(history.title)
                .setIcon(IconCompat.createWithResource(appContext, R.drawable.sc_history_48dp))
                .setIntent(intent)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(appContext, shortcut)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update the continue reading shortcut" }
        }
    }

    fun remove(context: Context) {
        ShortcutManagerCompat.removeDynamicShortcuts(context.applicationContext, listOf(SHORTCUT_ID))
    }

    internal fun labelFor(title: String): String {
        return if (title.length <= MAX_LABEL_LENGTH) title else title.take(MAX_LABEL_LENGTH - 1).trimEnd() + "…"
    }
}
// KMK <--
