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
import eu.kanade.tachiyomi.data.backup.full.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.full.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.full.models.BackupSerializer
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.all.MergedSource
import exh.EXHMigrations
import exh.source.MERGED_SOURCE_ID
import okio.buffer
import okio.gzip
import okio.source
import java.util.Date

class FullBackupRestore(context: Context, notifier: BackupNotifier, private val online: Boolean) : AbstractBackupRestore<FullBackupManager>(context, notifier) {

    override suspend fun performRestore(uri: Uri): Boolean {
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
        backup.backupManga /* SY --> */.sortedBy { it.source == MERGED_SOURCE_ID } /* SY <-- */.forEach {
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

    private suspend fun restoreManga(backupManga: BackupManga, backupCategories: List<BackupCategory>, online: Boolean) {
        val manga = backupManga.getMangaImpl()
        val chapters = backupManga.getChaptersImpl()
        val categories = backupManga.categories
        val history = backupManga.history
        val tracks = backupManga.getTrackingImpl()
        // SY -->
        val mergedMangaReferences = backupManga.mergedMangaReferences
        val flatMetadata = backupManga.flatMetadata
        // SY <--

        // SY -->
        EXHMigrations.migrateBackupEntry(manga)
        // SY <--

        val source = backupManager.sourceManager.get(manga.source)
        val sourceName = sourceMapping[manga.source] ?: manga.source.toString()

        try {
            if (source != null || !online) {
                restoreMangaData(manga, source, chapters, categories, history, tracks, backupCategories, mergedMangaReferences, flatMetadata, online)
            } else {
                errors.add(Date() to "${manga.title} [$sourceName]: ${context.getString(R.string.source_not_found_name, sourceName)}")
            }
        } catch (e: Exception) {
            errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
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
    private suspend fun restoreMangaData(
        manga: Manga,
        source: Source?,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        online: Boolean
    ) {
        val dbManga = backupManager.getMangaFromDatabase(manga)

        db.inTransaction {
            if (dbManga == null) {
                // Manga not in database
                restoreMangaFetch(source, manga, chapters, categories, history, tracks, backupCategories, mergedMangaReferences, flatMetadata, online)
            } else { // Manga in database
                // Copy information from manga already in database
                backupManager.restoreMangaNoFetch(manga, dbManga)
                // Fetch rest of manga information
                restoreMangaNoFetch(source, manga, chapters, categories, history, tracks, backupCategories, mergedMangaReferences, flatMetadata, online)
            }
        }
    }

    /**
     * Fetches manga information
     *
     * @param manga manga that needs updating
     * @param chapters chapters of manga that needs updating
     * @param categories categories that need updating
     */
    private suspend fun restoreMangaFetch(
        source: Source?,
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        online: Boolean
    ) {
        try {
            val fetchedManga = backupManager.restoreMangaFetch(source, manga, online)
            fetchedManga.id ?: return

            if (online && source != null) {
                // SY -->
                if (source !is MergedSource) {
                    updateChapters(source, fetchedManga, chapters)
                }
                // SY <--
            } else {
                backupManager.restoreChaptersForMangaOffline(fetchedManga, chapters)
            }

            restoreExtraForManga(fetchedManga, categories, history, tracks, backupCategories, mergedMangaReferences, flatMetadata)

            updateTracking(fetchedManga, tracks)
        } catch (e: Exception) {
            errors.add(Date() to "${manga.title} - ${e.message}")
        }
    }

    private suspend fun restoreMangaNoFetch(
        source: Source?,
        backupManga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        online: Boolean
    ) {
        if (online && source != null) {
            if (/* SY --> */ source !is MergedSource && /* SY <-- */ !backupManager.restoreChaptersForManga(backupManga, chapters)) {
                updateChapters(source, backupManga, chapters)
            }
        } else {
            backupManager.restoreChaptersForMangaOffline(backupManga, chapters)
        }

        restoreExtraForManga(backupManga, categories, history, tracks, backupCategories, mergedMangaReferences, flatMetadata)

        updateTracking(backupManga, tracks)
    }

    private suspend fun restoreExtraForManga(manga: Manga, categories: List<Int>, history: List<BackupHistory>, tracks: List<Track>, backupCategories: List<BackupCategory>, mergedMangaReferences: List<BackupMergedMangaReference>, flatMetadata: BackupFlatMetadata?) {
        // Restore categories
        backupManager.restoreCategoriesForManga(manga, categories, backupCategories)

        // Restore history
        backupManager.restoreHistoryForManga(history)

        // Restore tracking
        backupManager.restoreTrackForManga(manga, tracks)

        // SY -->
        // Restore merged manga references if its a merged manga
        backupManager.restoreMergedMangaReferencesForManga(manga, mergedMangaReferences)

        // Restore flat metadata for metadata sources
        flatMetadata?.let { backupManager.restoreFlatMetadata(manga, it) }
        // SY <--
    }
}
