package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.track.TrackManager
import exh.merged.sql.models.MergedMangaReference
import exh.metadata.metadata.base.FlatMetadata
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSource
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import kotlin.math.max

class MangaRestorer {
    private val handler: DatabaseHandler = Injekt.get()
    private val getCategories: GetCategories = Injekt.get()
    private val getManga: GetManga = Injekt.get()
    private val getMangaByUrlAndSource: GetMangaByUrlAndSource = Injekt.get()
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
    private val upsertHistory: UpsertHistory = Injekt.get()
    private val getTracks: GetTracks = Injekt.get()
    private val insertTrack: InsertTrack = Injekt.get()
    private val trackManager: TrackManager = Injekt.get()
    private val setCustomMangaInfo: SetCustomMangaInfo = Injekt.get()

    suspend fun sortByUrl(mangas: List<BackupManga>): List<BackupManga> {
        val dbMangas = handler.awaitList { mangasQueries.getAll() }
        val urls = dbMangas.map { it.url }

        val (existing, nonExisting) = mangas.partition { urls.contains(it.url) }

        return nonExisting + existing
    }

    suspend fun restoreManga(
        backupManga: BackupManga,
        backupCategories: List<BackupCategory>,
    ) {
        val customManga = backupManga.getCustomMangaInfo()
        var manga = backupManga.getMangaImpl()

        val dbManga = getMangaByUrlAndSource.await(manga.url, manga.source)
        manga = if (dbManga == null) {
            val id = insertManga(manga)
            manga.copy(id = id)
        } else {
            val updatedManga = updateManga(manga, dbManga)
            manga.copy(
                id = updatedManga.id,
                viewerFlags = updatedManga.viewerFlags,
                chapterFlags = updatedManga.chapterFlags,
                updateStrategy = updatedManga.updateStrategy,
                notes = updatedManga.notes,
            )
        }

        customManga?.let {
            restoreEditedInfo(it.copy(id = manga.id))
        }

        restoreMangaDetails(
            manga = manga,
            chapters = backupManga.chapters,
            categories = backupManga.categories,
            backupCategories = backupCategories,
            history = backupManga.history,
            tracks = backupManga.tracking,
            excludedScanlators = backupManga.excludedScanlators,
            // SY -->
            mergedMangaReferences = backupManga.mergedMangaReferences,
            flatMetadata = backupManga.flatMetadata?.toFlatMetadata(manga.id),
            // SY <--
        )
    }

    private fun updateManga(manga: Manga, dbManga: Manga): Manga {
        return dbManga.copy(
            favorite = manga.favorite || dbManga.favorite,
            viewerFlags = manga.viewerFlags,
            chapterFlags = manga.chapterFlags,
            updateStrategy = manga.updateStrategy,
            notes = manga.notes,
            initialized = manga.initialized || dbManga.initialized,
        )
    }

    private suspend fun updateManga(manga: Manga, newer: Manga): Manga {
        val updated = dbToBackupManga(manga, newer)
        if (updated != manga) {
            handler.await(true) {
                mangasQueries.update(
                    source = manga.source,
                    url = manga.url,
                    // SY -->
                    artist = manga.ogArtist,
                    author = manga.ogAuthor,
                    description = manga.ogDescription,
                    genre = manga.ogGenre?.joinToString(separator = ", "),
                    title = manga.ogTitle,
                    status = manga.ogStatus,
                    thumbnailUrl = manga.ogThumbnailUrl,
                    // SY <--
                    favorite = manga.favorite,
                    lastUpdate = manga.lastUpdate,
                    nextUpdate = null,
                    calculateInterval = null,
                    initialized = manga.initialized,
                    viewer = manga.viewerFlags,
                    chapterFlags = manga.chapterFlags,
                    coverLastModified = manga.coverLastModified,
                    dateAdded = manga.dateAdded,
                    mangaId = manga.id,
                    updateStrategy = manga.updateStrategy.let(UpdateStrategyColumnAdapter::encode),
                    version = manga.version,
                    isSyncing = 1,
                    notes = manga.notes,
                    // KMK -->
                    bannerUrl = manga.ogBannerUrl,
                    // KMK <--
                )
            }
        }
        return manga
    }

    private suspend fun restoreNewManga(
        manga: Manga,
    ): Manga {
        return manga
    }

    private fun dbToBackupManga(manga: Manga, newer: Manga): Manga {
        return manga.copy(
            favorite = manga.favorite || newer.favorite,
            viewerFlags = newer.viewerFlags,
            chapterFlags = newer.chapterFlags,
            updateStrategy = newer.updateStrategy,
            notes = newer.notes,
            initialized = manga.initialized || newer.initialized,
            version = newer.version,
        )
    }

    suspend fun updateManga(manga: Manga): Manga {
        handler.await(true) {
            mangasQueries.update(
                source = manga.source,
                url = manga.url,
                // SY -->
                artist = manga.ogArtist,
                author = manga.ogAuthor,
                description = manga.ogDescription,
                genre = manga.ogGenre?.joinToString(separator = ", "),
                title = manga.ogTitle,
                status = manga.ogStatus,
                thumbnailUrl = manga.ogThumbnailUrl,
                // SY <--
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = manga.initialized,
                viewer = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                mangaId = manga.id,
                updateStrategy = manga.updateStrategy.let(UpdateStrategyColumnAdapter::encode),
                version = manga.version,
                isSyncing = 1,
                notes = manga.notes,
                // KMK -->
                bannerUrl = manga.ogBannerUrl,
                // KMK <--
            )
        }
        return manga
    }

    private suspend fun restoreNewMangaDetails(
        manga: Manga,
    ) {
    }

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    private suspend fun insertManga(manga: Manga): Long {
        return handler.awaitOneExecutable(true) {
            mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                // SY -->
                artist = manga.ogArtist,
                author = manga.ogAuthor,
                description = manga.ogDescription,
                genre = manga.ogGenre,
                title = manga.ogTitle,
                status = manga.ogStatus,
                thumbnailUrl = manga.ogThumbnailUrl,
                // SY <--
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
                version = manga.version,
                notes = manga.notes,
                // KMK -->
                bannerUrl = manga.ogBannerUrl,
                // KMK <--
            )
            mangasQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreMangaDetails(
        manga: Manga,
        chapters: List<BackupChapter>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
        excludedScanlators: List<String>,
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: FlatMetadata?,
        // SY <--
    ) {
        restoreChapters(manga, chapters)
        restoreCategories(manga, categories, backupCategories)
        restoreHistory(history)
        restoreTracking(manga, tracks)
        restoreExcludedScanlators(manga, excludedScanlators)
        // SY -->
        restoreMergedMangaReferences(mergedMangaReferences)
        restoreFlatMetadata(flatMetadata)
        // SY <--
    }

    private suspend fun restoreChapters(manga: Manga, chapters: List<BackupChapter>) {
        val dbChapters = getChaptersByMangaId.await(manga.id)

        val updates = chapters.fastMap { backupChapter ->
            val dbChapter = dbChapters.find { it.url == backupChapter.url }
            if (dbChapter != null) {
                var update = dbChapter.copy(
                    read = backupChapter.read || dbChapter.read,
                    bookmark = backupChapter.bookmark || dbChapter.bookmark,
                    lastPageRead = max(backupChapter.lastPageRead, dbChapter.lastPageRead),
                )
                if (backupChapter.dateFetch != 0L && backupChapter.dateFetch > dbChapter.dateFetch) {
                    update = update.copy(dateFetch = backupChapter.dateFetch)
                }
                if (backupChapter.dateUpload != 0L && backupChapter.dateUpload > dbChapter.dateUpload) {
                    update = update.copy(dateUpload = backupChapter.dateUpload)
                }
                if (dbChapter.chapterNumber != backupChapter.chapterNumber && backupChapter.chapterNumber != -1f) {
                    update = update.copy(chapterNumber = backupChapter.chapterNumber)
                }
                update
            } else {
                null
            }
        }.filterNotNull()

        if (updates.isNotEmpty()) {
            handler.await(true) {
                updates.forEach { chapter ->
                    chaptersQueries.update(
                        mangaId = chapter.mangaId,
                        url = chapter.url,
                        name = chapter.name,
                        scanlator = chapter.scanlator,
                        read = chapter.read,
                        bookmark = chapter.bookmark,
                        lastPageRead = chapter.lastPageRead,
                        chapterNumber = chapter.chapterNumber.toDouble(),
                        sourceOrder = chapter.sourceOrder,
                        dateFetch = chapter.dateFetch,
                        dateUpload = chapter.dateUpload,
                        chapterId = chapter.id,
                        isSyncing = 1,
                        lastModifiedAt = chapter.lastModifiedAt,
                        version = chapter.version,
                    )
                }
            }
        }
    }

    private suspend fun restoreChapters(manga: Manga, dbChapters: List<Chapter>, chapters: List<BackupChapter>) {
        val updates = chapters.mapNotNull { backupChapter ->
            val dbChapter = dbChapters.find { it.url == backupChapter.url }
            if (dbChapter != null) {
                ChapterUpdate(
                    id = dbChapter.id,
                    read = backupChapter.read || dbChapter.read,
                    bookmark = backupChapter.bookmark || dbChapter.bookmark,
                    lastPageRead = max(backupChapter.lastPageRead, dbChapter.lastPageRead).toLong(),
                )
            } else {
                null
            }
        }

        if (updates.isNotEmpty()) {
            handler.await(true) {
                updates.forEach { update ->
                    chaptersQueries.update(
                        mangaId = null,
                        url = null,
                        name = null,
                        scanlator = null,
                        read = update.read,
                        bookmark = update.bookmark,
                        lastPageRead = update.lastPageRead,
                        chapterNumber = null,
                        sourceOrder = null,
                        dateFetch = null,
                        dateUpload = null,
                        chapterId = update.id,
                        isSyncing = 1,
                        lastModifiedAt = null,
                        version = null,
                    )
                }
            }
        }
    }

    private suspend fun restoreCategories(
        manga: Manga,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getCategories.subscribe(manga.id).first()
        val dbCategoriesMap = getCategories.subscribe().first()
            .associateBy { it.name }

        val mangaCategories = categories.mapNotNull { order ->
            val backupCategory = backupCategories.find { it.order == order } ?: return@mapNotNull null
            val dbCategory = dbCategoriesMap[backupCategory.name] ?: return@mapNotNull null
            dbCategory
        }

        if (mangaCategories.isNotEmpty()) {
            val categoriesToAdd = mangaCategories.filterNot { it in dbCategories }
            val categoriesToRemove = dbCategories.filterNot { it in mangaCategories }

            val mangaCategoryAddList = categoriesToAdd.map { category ->
                MangaCategory(
                    mangaId = manga.id,
                    categoryId = category.id,
                )
            }

            if (mangaCategoryAddList.isNotEmpty() || categoriesToRemove.isNotEmpty()) {
                handler.await(true) {
                    mangaCategoryAddList.forEach { mangaCategory ->
                        mangas_categoriesQueries.insert(
                            mangaCategory.mangaId,
                            mangaCategory.categoryId,
                        )
                    }
                    categoriesToRemove.forEach { category ->
                        mangas_categoriesQueries.deleteMangaCategory(
                            manga.id,
                            category.id,
                        )
                    }
                }
            }
        }
    }

    private suspend fun restoreCategories(
        manga: Manga,
        categories: List<Long>,
        dbCategories: List<Category>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategoriesMap = dbCategories.associateBy { it.name }

        val mangaCategories = categories.mapNotNull { order ->
            val backupCategory = backupCategories.find { it.order == order } ?: return@mapNotNull null
            val dbCategory = dbCategoriesMap[backupCategory.name] ?: return@mapNotNull null
            dbCategory
        }

        if (mangaCategories.isNotEmpty()) {
            handler.await(true) {
                mangaCategories.forEach { category ->
                    mangas_categoriesQueries.insert(
                        manga.id,
                        category.id,
                    )
                }
            }
        }
    }

    private suspend fun restoreHistory(history: List<BackupHistory>) {
        val toUpdate = history.map { backupHistory ->
            val dbHistory = handler.awaitOneOrNull {
                historyQueries.getHistoryByChapterUrl(backupHistory.url)
            }
            if (dbHistory != null) {
                HistoryUpdate(
                    chapterId = dbHistory.chapter_id,
                    readAt = Instant.ofEpochMilli(max(backupHistory.lastRead, dbHistory.last_read?.toEpochMilli() ?: 0)),
                    sessionReadDuration = max(backupHistory.readDuration, dbHistory.time_read),
                )
            } else {
                val dbChapter = handler.awaitOneOrNull {
                    chaptersQueries.getChapterByUrl(backupHistory.url)
                }
                if (dbChapter != null) {
                    HistoryUpdate(
                        chapterId = dbChapter._id,
                        readAt = Instant.ofEpochMilli(backupHistory.lastRead),
                        sessionReadDuration = backupHistory.readDuration,
                    )
                } else {
                    null
                }
            }
        }.filterNotNull()

        if (toUpdate.isNotEmpty()) {
            toUpdate.forEach {
                upsertHistory.await(it)
            }
        }
    }

    private suspend fun restoreTracking(manga: Manga, tracking: List<BackupTracking>) {
        val dbTracking = getTracks.await(manga.id)

        val toUpdate = tracking.mapNotNull { backupTracking ->
            val track = backupTracking.getTrackingImpl()
            val dbTrack = dbTracking.find { it.trackerId == track.trackerId }
            if (dbTrack != null) {
                // Update tracking
                var updated = dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    title = track.title,
                    lastChapterRead = max(track.lastChapterRead, dbTrack.lastChapterRead),
                    totalChapters = track.totalChapters,
                    status = track.status,
                    score = track.score,
                    trackingUrl = track.trackingUrl,
                    startDate = track.startDate,
                    finishDate = track.finishDate,
                )
                if (updated != dbTrack) {
                    updated
                } else {
                    null
                }
            } else {
                // Insert new tracking
                track.copy(mangaId = manga.id)
            }
        }

        if (toUpdate.isNotEmpty()) {
            toUpdate.forEach { track ->
                insertTrack.await(track)
            }
        }
    }

    private suspend fun restoreTracking(manga: Manga, dbTracking: List<Track>, tracking: List<BackupTracking>) {
        val toUpdate = tracking.mapNotNull { backupTracking ->
            val track = backupTracking.getTrackingImpl()
            val dbTrack = dbTracking.find { it.trackerId == track.trackerId }
            if (dbTrack != null) {
                val updated = dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    title = track.title,
                    lastChapterRead = max(track.lastChapterRead, dbTrack.lastChapterRead),
                    totalChapters = track.totalChapters,
                    status = track.status,
                    score = track.score,
                    trackingUrl = track.trackingUrl,
                    startDate = track.startDate,
                    finishDate = track.finishDate,
                )
                if (updated != dbTrack) {
                    updated
                } else {
                    null
                }
            } else {
                track.copy(mangaId = manga.id)
            }
        }

        if (toUpdate.isNotEmpty()) {
            toUpdate.forEach { track ->
                insertTrack.await(track)
            }
        }
    }

    private suspend fun restoreExcludedScanlators(manga: Manga, excludedScanlators: List<String>) {
        if (excludedScanlators.isNotEmpty()) {
            handler.await(true) {
                excludedScanlators.forEach { scanlator ->
                    excluded_scanlatorsQueries.insert(
                        manga.id,
                        scanlator,
                    )
                }
            }
        }
    }

    // SY -->
    private suspend fun restoreMergedMangaReferences(references: List<BackupMergedMangaReference>) {
        if (references.isNotEmpty()) {
            val dbReferences = handler.awaitList { mergedQueries.selectAll(MergedMangaReference::map) }
            val urls = dbReferences.map { it.mangaUrl }

            val nonExisting = references.filterNot { urls.contains(it.mangaUrl) }
            if (nonExisting.isNotEmpty()) {
                handler.await(true) {
                    nonExisting.forEach { ref ->
                        mergedQueries.insert(
                            infoManga = ref.infoManga,
                            getChapters = ref.getChapters,
                            mangaUrl = ref.mangaUrl,
                            mangaSource = ref.mangaSource,
                            mergeUrl = ref.mergeUrl,
                            mangaId = null,
                            mergeId = ref.mergeId,
                        )
                    }
                }
            }
        }
    }

    private suspend fun restoreFlatMetadata(flatMetadata: FlatMetadata?) {
        flatMetadata ?: return
        handler.await(true) {
            search_metadataQueries.insert(
                flatMetadata.metadata.mangaId,
                flatMetadata.metadata.uploader,
                flatMetadata.metadata.extra,
                flatMetadata.metadata.indexedAt,
            )
            search_tagsQueries.deleteByManga(flatMetadata.metadata.mangaId)
            flatMetadata.tags.forEach { tag ->
                search_tagsQueries.insert(
                    null,
                    flatMetadata.metadata.mangaId,
                    tag.namespace,
                    tag.name,
                    tag.type,
                )
            }
            search_titlesQueries.deleteByManga(flatMetadata.metadata.mangaId)
            flatMetadata.titles.forEach { title ->
                search_titlesQueries.insert(
                    null,
                    flatMetadata.metadata.mangaId,
                    title.title,
                    title.type,
                )
            }
        }
    }

    private fun restoreEditedInfo(mangaJson: CustomMangaInfo?) {
        mangaJson ?: return
        setCustomMangaInfo.set(mangaJson)
    }

    private fun BackupManga.getCustomMangaInfo(): CustomMangaInfo? {
        if (customTitle != null ||
            customArtist != null ||
            customAuthor != null ||
            customThumbnailUrl != null ||
            customDescription != null ||
            customGenre != null ||
            customStatus != 0 ||
            customBannerUrl != null
        ) {
            return CustomMangaInfo(
                id = 0L,
                title = customTitle,
                author = customAuthor,
                artist = customArtist,
                thumbnailUrl = customThumbnailUrl,
                description = customDescription,
                genre = customGenre,
                status = customStatus.takeUnless { it == 0 }?.toLong(),
                bannerUrl = customBannerUrl,
            )
        }
        return null
    }
    // SY <--

    private fun Track.forComparison() = this.copy(id = 0L, mangaId = 0L)

    /**
     * Returns true if track is similar to [other]
     */
    private fun Track.isSimilar(other: Track): Boolean {
        if (trackerId != other.trackerId) return false
        val item = trackManager.get(trackerId) ?: return false

        return if (item.isFormSupported) {
            remoteId == other.remoteId
        } else {
            title == other.title
        }
    }
}
