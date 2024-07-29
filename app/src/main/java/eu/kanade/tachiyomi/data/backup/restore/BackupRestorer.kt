package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.FeedRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.SavedSearchRestorer
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
    private val isSync: Boolean,

    private val categoriesRestorer: CategoriesRestorer = CategoriesRestorer(),
    private val preferenceRestorer: PreferenceRestorer = PreferenceRestorer(context),
    private val mangaRestorer: MangaRestorer = MangaRestorer(isSync),
    // SY -->
    private val savedSearchRestorer: SavedSearchRestorer = SavedSearchRestorer(),
    // SY <--
    // KMK -->
    private val feedRestorer: FeedRestorer = FeedRestorer(),
    // KMK <--
) {

    private var restoreAmount = 0
    private var restoreProgress = 0
    private val errors = mutableListOf<Pair<Date, String>>()

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        restoreFromFile(uri, options)

        val time = System.currentTimeMillis() - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(
            time,
            errors.size,
            logFile.parent,
            logFile.name,
            isSync,
        )
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val backup = BackupDecoder(context).decode(uri)

        // Store source mapping for error messages
        val backupMaps = backup.backupSources + backup.backupBrokenSources.map { it.toBackupSource() }
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        if (options.libraryEntries) {
            restoreAmount += backup.backupManga.size
        }
        if (options.categories) {
            restoreAmount += 1
        }
        // SY -->
        if (options.savedSearches) {
            restoreAmount += 1
        }
        // SY <--
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }

        coroutineScope {
            if (options.categories) {
                restoreCategories(backup.backupCategories)
            }
            // SY -->
            if (options.savedSearches) {
                restoreSavedSearches(
                    backup.backupSavedSearches,
                    // KMK -->
                    backup.backupFeeds,
                    // KMK <--
                )
            }
            // SY <--
            if (options.appSettings) {
                restoreAppPreferences(backup.backupPreferences)
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            if (options.libraryEntries) {
                restoreManga(backup.backupManga, if (options.categories) backup.backupCategories else emptyList())
            }

            // TODO: optionally trigger online library + tracker update
        }
    }

    private fun CoroutineScope.restoreCategories(backupCategories: List<BackupCategory>) = launch {
        ensureActive()
        categoriesRestorer(backupCategories)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.categories),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    // SY -->
    private fun CoroutineScope.restoreSavedSearches(
        backupSavedSearches: List<BackupSavedSearch>,
        // KMK -->
        backupFeeds: List<BackupFeed>,
        // KMK <--
    ) = launch {
        ensureActive()
        // KMK -->
        feedRestorer.restoreFeeds(backupFeeds)
        // KMK <--
        savedSearchRestorer.restoreSavedSearches(backupSavedSearches)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(KMR.strings.saved_searches_feeds),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }
    // SY <--

    private fun CoroutineScope.restoreManga(
        backupMangas: List<BackupManga>,
        backupCategories: List<BackupCategory>,
    ) = launch {
        mangaRestorer.sortByNew(backupMangas)
            /* SY --> */.sortedBy { it.source == MERGED_SOURCE_ID } /* SY <-- */
            .forEach {
                ensureActive()

                try {
                    mangaRestorer.restore(it, backupCategories)
                } catch (e: Exception) {
                    val sourceName = sourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(it.title, restoreProgress, restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreAppPreferences(preferences: List<BackupPreference>) = launch {
        ensureActive()
        preferenceRestorer.restoreApp(preferences)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.app_settings),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("tachiyomi_restore.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }
}
