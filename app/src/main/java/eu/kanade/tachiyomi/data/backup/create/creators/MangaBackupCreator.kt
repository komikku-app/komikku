package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupCreateFlags
import eu.kanade.tachiyomi.data.backup.create.BackupCreateFlags.BACKUP_CUSTOM_INFO
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.backupChapterMapper
import eu.kanade.tachiyomi.data.backup.models.backupMergedMangaReferenceMapper
import eu.kanade.tachiyomi.data.backup.models.backupTrackMapper
import eu.kanade.tachiyomi.source.online.MetadataSource
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    // SY -->
    private val sourceManager: SourceManager = Injekt.get(),
    private val getCustomMangaInfo: GetCustomMangaInfo = Injekt.get(),
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
    // SY <--
) {

    suspend fun backupMangas(mangas: List<Manga>, flags: Int): List<BackupManga> {
        return mangas.map {
            backupManga(it, flags)
        }
    }

    private suspend fun backupManga(manga: Manga, options: Int): BackupManga {
        // Entry for this manga
        val mangaObject = BackupManga.copyFrom(
            manga,
            // SY -->
            if (options and BACKUP_CUSTOM_INFO == BACKUP_CUSTOM_INFO) {
                getCustomMangaInfo.get(manga.id)
            } else {
                null
            }, /* SY <-- */
        )

        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            mangaObject.mergedMangaReferences = handler.awaitList {
                mergedQueries.selectByMergeId(manga.id, backupMergedMangaReferenceMapper)
            }
        }

        val source = sourceManager.get(manga.source)?.getMainSource<MetadataSource<*, *>>()
        if (source != null) {
            getFlatMetadataById.await(manga.id)?.let { flatMetadata ->
                mangaObject.flatMetadata = BackupFlatMetadata.copyFrom(flatMetadata)
            }
        }
        // SY <--

        // Check if user wants chapter information in backup
        if (options and BackupCreateFlags.BACKUP_CHAPTER == BackupCreateFlags.BACKUP_CHAPTER) {
            // Backup all the chapters
            handler.awaitList {
                chaptersQueries.getChaptersByMangaId(
                    mangaId = manga.id,
                    applyScanlatorFilter = 0, // false
                    mapper = backupChapterMapper,
                )
            }
                .takeUnless(List<BackupChapter>::isEmpty)
                ?.let { mangaObject.chapters = it }
        }

        // Check if user wants category information in backup
        if (options and BackupCreateFlags.BACKUP_CATEGORY == BackupCreateFlags.BACKUP_CATEGORY) {
            // Backup categories for this manga
            val categoriesForManga = getCategories.await(manga.id)
            if (categoriesForManga.isNotEmpty()) {
                mangaObject.categories = categoriesForManga.map { it.order }
            }
        }

        // Check if user wants track information in backup
        if (options and BackupCreateFlags.BACKUP_TRACK == BackupCreateFlags.BACKUP_TRACK) {
            val tracks = handler.awaitList { manga_syncQueries.getTracksByMangaId(manga.id, backupTrackMapper) }
            if (tracks.isNotEmpty()) {
                mangaObject.tracking = tracks
            }
        }

        // Check if user wants history information in backup
        if (options and BackupCreateFlags.BACKUP_HISTORY == BackupCreateFlags.BACKUP_HISTORY) {
            val historyByMangaId = getHistory.await(manga.id)
            if (historyByMangaId.isNotEmpty()) {
                val history = historyByMangaId.map { history ->
                    val chapter = handler.awaitOne { chaptersQueries.getChapterById(history.chapterId) }
                    BackupHistory(chapter.url, history.readAt?.time ?: 0L, history.readDuration)
                }
                if (history.isNotEmpty()) {
                    mangaObject.history = history
                }
            }
        }

        return mangaObject
    }
}
