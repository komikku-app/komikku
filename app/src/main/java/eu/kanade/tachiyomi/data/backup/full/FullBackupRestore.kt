package eu.kanade.tachiyomi.data.backup.full

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.AbstractBackupRestore
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.full.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.full.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.full.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.full.models.BackupManga
import eu.kanade.tachiyomi.data.backup.full.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.full.models.BackupSerializer
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.source.Source
import exh.EXHMigrations
import java.util.Date
import kotlinx.serialization.ExperimentalSerializationApi
import okio.buffer
import okio.gzip
import okio.source
import rx.Observable

@OptIn(ExperimentalSerializationApi::class)
class FullBackupRestore(context: Context, notifier: BackupNotifier, private val online: Boolean) : AbstractBackupRestore<FullBackupManager>(context, notifier) {

    override fun performRestore(uri: Uri): Boolean {
        // SY -->
        throttleManager.resetThrottle()
        // SY <--
        backupManager = FullBackupManager(context)

        val backupString = context.contentResolver.openInputStream(uri)!!.source().gzip().buffer().use { it.readByteArray() }
        val backup = backupManager.parser.decodeFromByteArray(BackupSerializer, backupString)

        restoreAmount = backup.backupManga.size + 1 /* SY --> */ + 1 /* SY <-- */ // +1 for categories, +1 for saved searches

        // Restore categories
        if (backup.backupCategories.isNotEmpty()) {
            restoreCategories(backup.backupCategories)
        }

        // SY -->
        if (backup.backupSavedSearches.isNotEmpty()) {
            restoreSavedSearches(backup.backupSavedSearches)
        }
        // SY <--

        // Store source mapping for error messages
        sourceMapping = backup.backupSources.map { it.sourceId to it.name }.toMap()

        // Restore individual manga, sort by merged source so that merged source manga go last and merged references get the proper ids
        backup.backupManga.forEach {
            if (job?.isActive != true) {
                return false
            }

            restoreManga(it, backup.backupCategories, online)
        }

        return true
    }

    private fun restoreCategories(backupCategories: List<BackupCategory>) {
        db.inTransaction {
            backupManager.restoreCategories(backupCategories)
        }

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.categories))
    }

    // SY -->
    private fun restoreSavedSearches(backupSavedSearches: List<BackupSavedSearch>) {
        backupManager.restoreSavedSearches(backupSavedSearches)

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.saved_searches))
    }
    // SY <--

    private fun restoreManga(backupManga: BackupManga, backupCategories: List<BackupCategory>, online: Boolean) {
        var manga = backupManga.getMangaImpl()
        val chapters = backupManga.getChaptersImpl()
        val categories = backupManga.categories
        val history = backupManga.history
        val tracks = backupManga.getTrackingImpl()
        // SY -->
        val flatMetadata = backupManga.flatMetadata
        // SY <--

        // SY -->
        manga = EXHMigrations.migrateBackupEntry(manga)
        // SY <--

        try {
            val source = backupManager.sourceManager.get(manga.source)
            if (source != null || !online) {
                restoreMangaData(manga, source, chapters, categories, history, tracks, backupCategories, flatMetadata, online)
            } else {
                val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
                errors.add(Date() to "${manga.title} - ${context.getString(R.string.source_not_found_name, sourceName)}")
            }
        } catch (e: Exception) {
            errors.add(Date() to "${manga.title} - ${e.message}")
        }

        restoreProgress += 1
        showRestoreProgress(restoreProgress, restoreAmount, manga.title)
    }

    /**
     * Returns a manga restore observable
     *
     * @param manga manga data from json
     * @param source source to get manga data from
     * @param chapters chapters data from json
     * @param categories categories data from json
     * @param history history data from json
     * @param tracks tracking data from json
     */
    private fun restoreMangaData(
        manga: Manga,
        source: Source?,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        flatMetadata: BackupFlatMetadata?,
        online: Boolean
    ) {
        val dbManga = backupManager.getMangaFromDatabase(manga)

        db.inTransaction {
            if (dbManga == null) {
                // Manga not in database
                restoreMangaFetch(source, manga, chapters, categories, history, tracks, backupCategories, flatMetadata, online)
            } else { // Manga in database
                // Copy information from manga already in database
                backupManager.restoreMangaNoFetch(manga, dbManga)
                // Fetch rest of manga information
                restoreMangaNoFetch(source, manga, chapters, categories, history, tracks, backupCategories, flatMetadata, online)
            }
        }
    }

    /**
     * [Observable] that fetches manga information
     *
     * @param manga manga that needs updating
     * @param chapters chapters of manga that needs updating
     * @param categories categories that need updating
     */
    private fun restoreMangaFetch(
        source: Source?,
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        flatMetadata: BackupFlatMetadata?,
        online: Boolean
    ) {
        backupManager.restoreMangaFetchObservable(source, manga, online)
            .doOnError {
                errors.add(Date() to "${manga.title} - ${it.message}")
            }
            .filter { it.id != null }
            .flatMap {
                if (online && source != null) {
                    Observable.just(manga)
                } else {
                    backupManager.restoreChaptersForMangaOffline(it, chapters)
                    Observable.just(manga)
                }
            }
            .doOnNext {
                restoreExtraForManga(it, categories, history, tracks, backupCategories, flatMetadata)
            }
            .flatMap {
                trackingFetchObservable(it, tracks)
            }
            .subscribe()
    }

    private fun restoreMangaNoFetch(
        source: Source?,
        backupManga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        flatMetadata: BackupFlatMetadata?,
        online: Boolean
    ) {
        Observable.just(backupManga)
            .flatMap { manga ->
                if (online && source != null) {
                    if (!backupManager.restoreChaptersForManga(manga, chapters)) {
                        chapterFetchObservable(source, manga, chapters)
                            .map { manga }
                    } else {
                        Observable.just(manga)
                    }
                } else {
                    backupManager.restoreChaptersForMangaOffline(manga, chapters)
                    Observable.just(manga)
                }
            }
            .doOnNext {
                restoreExtraForManga(it, categories, history, tracks, backupCategories, flatMetadata)
            }
            .flatMap { manga ->
                trackingFetchObservable(manga, tracks)
            }
            .subscribe()
    }

    private fun restoreExtraForManga(manga: Manga, categories: List<Int>, history: List<BackupHistory>, tracks: List<Track>, backupCategories: List<BackupCategory>, flatMetadata: BackupFlatMetadata?) {
        // Restore categories
        backupManager.restoreCategoriesForManga(manga, categories, backupCategories)

        // Restore history
        backupManager.restoreHistoryForManga(history)

        // Restore tracking
        backupManager.restoreTrackForManga(manga, tracks)

        // SY -->

        // Restore flat metadata for metadata sources
        flatMetadata?.let { backupManager.restoreFlatMetadata(manga, it) }
        // SY <--
    }
}
