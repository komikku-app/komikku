package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.backupChapterMapper
import eu.kanade.tachiyomi.data.backup.models.backupMergedMangaReferenceMapper
import eu.kanade.tachiyomi.data.backup.models.backupTrackMapper
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.model.CustomMangaInfo
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

    suspend operator fun invoke(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        return mangas.map {
            backupManga(it, options)
        }
    }

    private suspend fun backupManga(manga: Manga, options: BackupOptions): BackupManga {
        // Entry for this manga
        val mangaObject = manga.toBackupManga(
            // SY -->
            if (options.customInfo) {
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

        mangaObject.excludedScanlators = handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(manga.id)
        }

        if (options.chapters) {
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

        if (options.categories) {
            // Backup categories for this manga
            val categoriesForManga = getCategories.await(manga.id)
            if (categoriesForManga.isNotEmpty()) {
                mangaObject.categories = categoriesForManga.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = handler.awaitList { manga_syncQueries.getTracksByMangaId(manga.id, backupTrackMapper) }
            if (tracks.isNotEmpty()) {
                mangaObject.tracking = tracks
            }
        }

        if (options.history) {
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

private fun Manga.toBackupManga(/* SY --> */customMangaInfo: CustomMangaInfo?/* SY <-- */) =
    BackupManga(
        url = this.url,
        title = this.title,
        artist = this.artist,
        author = this.author,
        description = this.description,
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        // SY -->
    ).also { backupManga ->
        customMangaInfo?.let {
            backupManga.customTitle = it.title
            backupManga.customArtist = it.artist
            backupManga.customAuthor = it.author
            backupManga.customThumbnailUrl = it.thumbnailUrl
            backupManga.customDescription = it.description
            backupManga.customGenre = it.genre
            backupManga.customStatus = it.status?.toInt() ?: 0
        }
    }
// SY <--
