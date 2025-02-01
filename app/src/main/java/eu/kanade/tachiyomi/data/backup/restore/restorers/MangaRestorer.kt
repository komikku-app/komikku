package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import exh.EXHMigrations
import exh.source.MERGED_SOURCE_ID
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.manga.MergedMangaMapper
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.max
import kotlin.math.min

class MangaRestorer(
    private var isSync: Boolean = false,

    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    fetchInterval: FetchInterval = Injekt.get(),
    // SY -->
    private val setCustomMangaInfo: SetCustomMangaInfo = Injekt.get(),
    private val insertFlatMetadata: InsertFlatMetadata = Injekt.get(),
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
    // SY <--
    private val getAllManga: GetAllManga = Injekt.get(),
) {
    private var now = ZonedDateTime.now()
    private var currentFetchWindow = fetchInterval.getWindow(now)

    init {
        now = ZonedDateTime.now()
        currentFetchWindow = fetchInterval.getWindow(now)
    }

    suspend fun sortByNew(backupMangas: List<BackupManga>): List<BackupManga> {
        val urlsBySource = handler.awaitList { mangasQueries.getAllMangaSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupMangas
            .sortedWith(
                // KMK -->
                compareBy<BackupManga> { it.source == MERGED_SOURCE_ID }
                    // KMK <--
                    .then(compareBy { it.url in urlsBySource[it.source].orEmpty() })
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    /**
     * Restore a list of mangas
     */
    suspend fun restoreMangas(
        backupMangas: List<BackupManga>,
    ): List<Manga> {
        return handler.await(inTransaction = true) {
            val bkMangas = backupMangas.map {
                EXHMigrations.migrateBackupEntry(it.getMangaImpl())
            }
            val dbMangas = getAllManga.await()
                .groupBy { it.source }
                .toList()
                .associate { it.first to it.second.associateBy { manga -> manga.url } }

            val (existingMangas, newMangas) = bkMangas.map { backupManga ->
                val dbManga = dbMangas[backupManga.source]?.get(backupManga.url)
                if (dbManga == null) {
                    backupManga
                } else {
                    if (backupManga.version > dbManga.version) {
                        dbManga.copyFrom(backupManga)
                    } else {
                        backupManga.copyFrom(dbManga).copy(id = dbManga.id)
                    }
                }
            }.partition { it.id > 0 }

            insertMangasBulk(newMangas) + updateMangas(existingMangas)
        }
    }

    /**
     * Restore a single manga
     */
    suspend fun restore(
        backupManga: BackupManga,
        restoredManga: Manga,
        backupCategories: List<BackupCategory>,
    ) {
        handler.await(inTransaction = true) {

            restoreMangaDetails(
                manga = restoredManga,
                chapters = backupManga.chapters,
                categories = backupManga.categories,
                backupCategories = backupCategories,
                history = backupManga.history,
                tracks = backupManga.tracking,
                excludedScanlators = backupManga.excludedScanlators,
                // SY -->
                mergedMangaReferences = backupManga.mergedMangaReferences,
                flatMetadata = backupManga.flatMetadata,
                customManga = backupManga.getCustomMangaInfo(),
                // SY <--
            )

            if (isSync) {
                mangasQueries.resetIsSyncing()
                chaptersQueries.resetIsSyncing()
            }
        }
    }

    private fun Manga.copyFrom(newer: Manga): Manga {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            // SY -->
            ogAuthor = newer.author,
            ogArtist = newer.artist,
            ogDescription = newer.description,
            ogGenre = newer.genre,
            ogThumbnailUrl = newer.thumbnailUrl,
            ogStatus = newer.status,
            // SY <--
            initialized = this.initialized || newer.initialized,
            version = newer.version,
        )
    }

    suspend fun updateManga(manga: Manga): Manga {
        handler.await(true) {
            mangasQueries.update(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre?.joinToString(separator = ", "),
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
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
            )
        }
        return manga
    }

    suspend fun updateMangas(mangas: List<Manga>): List<Manga> {
        handler.await(true) {
            mangas.forEach { manga ->
                mangasQueries.update(
                    source = manga.source,
                    url = manga.url,
                    artist = manga.artist,
                    author = manga.author,
                    description = manga.description,
                    genre = manga.genre?.joinToString(separator = ", "),
                    title = manga.title,
                    status = manga.status,
                    thumbnailUrl = manga.thumbnailUrl,
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
                )
            }
        }
        return mangas
    }

    private suspend fun restoreChapters(manga: Manga, backupChapters: List<BackupChapter>) {
        val dbChaptersByUrl = getChaptersByMangaId.await(manga.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = backupChapters
            .mapNotNull { backupChapter ->
                val chapter = backupChapter.toChapterImpl().copy(mangaId = manga.id)
                val dbChapter = dbChaptersByUrl[chapter.url]

                when {
                    dbChapter == null -> chapter // New chapter
                    chapter.forComparison() == dbChapter.forComparison() -> null // Same state; skip
                    else -> updateChapterBasedOnSyncState(chapter, dbChapter) // Update existed chapter
                }
            }
            .partition { it.id > 0 }

        insertNewChapters(newChapters)
        updateExistingChapters(existingChapters)
    }

    private fun updateChapterBasedOnSyncState(chapter: Chapter, dbChapter: Chapter): Chapter {
        return if (isSync) {
            chapter.copy(
                id = dbChapter.id,
                bookmark = chapter.bookmark || dbChapter.bookmark,
                read = chapter.read,
                lastPageRead = chapter.lastPageRead,
                sourceOrder = chapter.sourceOrder,
                // KMK -->
                dateUpload = min(chapter.dateUpload, dbChapter.dateUpload),
                // KMK <--
            )
        } else {
            chapter.copyFrom(dbChapter)
                // KMK -->
                .copy(
                    id = dbChapter.id,
                    bookmark = chapter.bookmark || dbChapter.bookmark,
                    dateUpload = min(chapter.dateUpload, dbChapter.dateUpload),
                )
                // KMK <--
                .let {
                    when {
                        dbChapter.read && !it.read -> it.copy(read = true, lastPageRead = dbChapter.lastPageRead)
                        it.lastPageRead == 0L && dbChapter.lastPageRead != 0L -> it.copy(
                            lastPageRead = dbChapter.lastPageRead,
                        )
                        else -> it
                    }
                }
        }
    }

    private fun Chapter.forComparison() =
        this.copy(
            id = 0L,
            mangaId = 0L,
            dateFetch = 0L,
            // KMK -->
            // dateUpload = 0L, some time source loses dateUpload so we overwrite with backup
            sourceOrder = 0L, // ignore sourceOrder since it will be updated on refresh
            // KMK <--
            lastModifiedAt = 0L,
            version = 0L,
        )

    private suspend fun insertNewChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.insert(
                    chapter.mangaId,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read,
                    chapter.bookmark,
                    chapter.lastPageRead,
                    chapter.chapterNumber,
                    chapter.sourceOrder,
                    chapter.dateFetch,
                    chapter.dateUpload,
                    chapter.version,
                )
            }
        }
    }

    private suspend fun updateExistingChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.update(
                    mangaId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    chapterNumber = null,
                    sourceOrder = if (isSync) chapter.sourceOrder else null,
                    dateFetch = null,
                    // KMK -->
                    dateUpload = chapter.dateUpload,
                    // KMK <--
                    chapterId = chapter.id,
                    version = chapter.version,
                    isSyncing = 1,
                )
            }
        }
    }

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    suspend fun insertMangasBulk(mangas: List<Manga>): List<Manga> {
        return handler.await(inTransaction = true) {
            mangas.map { manga ->
                mangasQueries.insert(
                    source = manga.source,
                    url = manga.url,
                    artist = manga.artist,
                    author = manga.author,
                    description = manga.description,
                    genre = manga.genre,
                    title = manga.title,
                    status = manga.status,
                    thumbnailUrl = manga.thumbnailUrl,
                    favorite = manga.favorite,
                    lastUpdate = manga.lastUpdate,
                    nextUpdate = manga.nextUpdate,
                    calculateInterval = manga.fetchInterval.toLong(),
                    initialized = manga.initialized,
                    viewerFlags = manga.viewerFlags,
                    chapterFlags = manga.chapterFlags,
                    coverLastModified = manga.coverLastModified,
                    dateAdded = manga.dateAdded,
                    updateStrategy = manga.updateStrategy,
                    version = manga.version,
                )

                val lastInsertId = mangasQueries.selectLastInsertedRowId().executeAsOne()
                manga.copy(id = lastInsertId)
            }
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
        flatMetadata: BackupFlatMetadata?,
        customManga: CustomMangaInfo?,
        // SY <--
    ): Manga {
        restoreCategories(manga, categories, backupCategories)
        restoreChapters(manga, chapters)
        restoreTracking(manga, tracks)
        restoreHistory(manga, history)
        restoreExcludedScanlators(manga, excludedScanlators)
        updateManga.awaitUpdateFetchInterval(manga, now, currentFetchWindow)
        // SY -->
        restoreMergedMangaReferencesForManga(manga.id, mergedMangaReferences)
        flatMetadata?.let { restoreFlatMetadata(manga.id, it) }
        restoreEditedInfo(customManga?.copy(id = manga.id))
        // SY <--

        return manga
    }

    /**
     * Restores the categories a manga is in.
     *
     * @param manga the manga whose categories have to be restored.
     * @param categories the categories to restore.
     */
    private suspend fun restoreCategories(
        manga: Manga,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name }

        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val mangaCategoriesToUpdate = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.let { dbCategory ->
                    Pair(manga.id, dbCategory.id)
                }
            }
        }

        if (mangaCategoriesToUpdate.isNotEmpty()) {
            handler.await(true) {
                mangas_categoriesQueries.deleteMangaCategoryByMangaId(manga.id)
                mangaCategoriesToUpdate.forEach { (mangaId, categoryId) ->
                    mangas_categoriesQueries.insert(mangaId, categoryId)
                }
            }
        }
    }

    private suspend fun restoreHistory(manga: Manga, backupHistory: List<BackupHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            val dbHistory = handler.awaitOneOrNull { historyQueries.getHistoryByChapterUrl(manga.id, history.url) }
            val item = history.getHistoryImpl()

            if (dbHistory == null) {
                val chapter = handler.awaitList { chaptersQueries.getChapterByUrlAndMangaId(history.url, manga.id) }
                    .firstOrNull()
                return@mapNotNull if (chapter == null) {
                    // Chapter doesn't exist; skip
                    null
                } else {
                    // New history entry
                    item.copy(chapterId = chapter._id)
                }
            }

            // Update history entry
            item.copy(
                id = dbHistory._id,
                chapterId = dbHistory.chapter_id,
                readAt = max(item.readAt?.time ?: 0L, dbHistory.last_read?.time ?: 0L)
                    .takeIf { it > 0L }
                    ?.let { Date(it) },
                readDuration = max(item.readDuration, dbHistory.time_read) - dbHistory.time_read,
            )
        }

        if (toUpdate.isNotEmpty()) {
            handler.await(true) {
                toUpdate.forEach {
                    historyQueries.upsert(
                        it.chapterId,
                        it.readAt,
                        it.readDuration,
                    )
                }
            }
        }
    }

    private suspend fun restoreTracking(manga: Manga, backupTracks: List<BackupTracking>) {
        val dbTrackByTrackerId = getTracks.await(manga.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = backupTracks
            .mapNotNull {
                val track = it.getTrackImpl()
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        mangaId = manga.id,
                    )

                if (track.forComparison() == dbTrack.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing track
                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastChapterRead = max(dbTrack.lastChapterRead, track.lastChapterRead),
                )
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            insertTrack.awaitAll(newTracks)
        }
        if (existingTracks.isNotEmpty()) {
            handler.await(true) {
                existingTracks.forEach { track ->
                    manga_syncQueries.update(
                        track.mangaId,
                        track.trackerId,
                        track.remoteId,
                        track.libraryId,
                        track.title,
                        track.lastChapterRead,
                        track.totalChapters,
                        track.status,
                        track.score,
                        track.remoteUrl,
                        track.startDate,
                        track.finishDate,
                        track.id,
                    )
                }
            }
        }
    }

    // SY -->
    /**
     * Restore the categories from Json
     *
     * @param mergeMangaId the merge manga for the references
     * @param backupMergedMangaReferences the list of backup manga references for the merged manga
     */
    private suspend fun restoreMergedMangaReferencesForManga(
        mergeMangaId: Long,
        backupMergedMangaReferences: List<BackupMergedMangaReference>,
    ) {
        // Get merged manga references from file and from db
        val dbMergedMangaReferences = handler.awaitList {
            mergedQueries.selectAll(MergedMangaMapper::map)
        }

        // Iterate over them
        backupMergedMangaReferences.forEach { backupMergedMangaReference ->
            // If the backupMergedMangaReference isn't in the db,
            // remove the id and insert a new backupMergedMangaReference
            // Store the inserted id in the backupMergedMangaReference
            if (dbMergedMangaReferences.none {
                    backupMergedMangaReference.mergeUrl == it.mergeUrl &&
                        backupMergedMangaReference.mangaUrl == it.mangaUrl
                }
            ) {
                // Let the db assign the id
                val mergedManga = handler.awaitOneOrNull {
                    mangasQueries.getMangaByUrlAndSource(
                        backupMergedMangaReference.mangaUrl,
                        backupMergedMangaReference.mangaSourceId,
                        MangaMapper::mapManga,
                    )
                } ?: return@forEach
                backupMergedMangaReference.getMergedMangaReference().run {
                    handler.await {
                        mergedQueries.insert(
                            infoManga = isInfoManga,
                            getChapterUpdates = getChapterUpdates,
                            chapterSortMode = chapterSortMode.toLong(),
                            chapterPriority = chapterPriority.toLong(),
                            downloadChapters = downloadChapters,
                            mergeId = mergeMangaId,
                            mergeUrl = mergeUrl,
                            mangaId = mergedManga.id,
                            mangaUrl = mangaUrl,
                            mangaSource = mangaSourceId,
                        )
                    }
                }
            }
        }
    }

    private suspend fun restoreFlatMetadata(mangaId: Long, backupFlatMetadata: BackupFlatMetadata) {
        if (getFlatMetadataById.await(mangaId) == null) {
            insertFlatMetadata.await(backupFlatMetadata.getFlatMetadata(mangaId))
        }
    }

    private fun restoreEditedInfo(mangaJson: CustomMangaInfo?) {
        mangaJson ?: return
        setCustomMangaInfo.set(mangaJson)
    }

    fun BackupManga.getCustomMangaInfo(): CustomMangaInfo? {
        if (customTitle != null ||
            customArtist != null ||
            customAuthor != null ||
            customThumbnailUrl != null ||
            customDescription != null ||
            customGenre != null ||
            customStatus != 0
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
            )
        }
        return null
    }
    // SY <--

    private fun Track.forComparison() = this.copy(id = 0L, mangaId = 0L)

    /**
     * Restores the excluded scanlators for the manga.
     *
     * @param manga the manga whose excluded scanlators have to be restored.
     * @param excludedScanlators the excluded scanlators to restore.
     */
    private suspend fun restoreExcludedScanlators(manga: Manga, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return
        val existingExcludedScanlators = handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(manga.id)
        }
        val toInsert = excludedScanlators.filter { it !in existingExcludedScanlators }
        if (toInsert.isNotEmpty()) {
            handler.await {
                toInsert.forEach {
                    excluded_scanlatorsQueries.insert(manga.id, it)
                }
            }
        }
    }
}
