package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.data.DatabaseHandler
import eu.kanade.data.manga.mangaMapper
import eu.kanade.data.toLong
import eu.kanade.domain.manga.interactor.GetFavorites
import eu.kanade.domain.manga.interactor.GetFlatMetadataById
import eu.kanade.domain.manga.interactor.GetMergedManga
import eu.kanade.domain.manga.interactor.InsertFlatMetadata
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import exh.metadata.metadata.base.FlatMetadata
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import data.Mangas as DbManga
import eu.kanade.domain.manga.model.Manga as DomainManga

abstract class AbstractBackupManager(protected val context: Context) {

    protected val handler: DatabaseHandler = Injekt.get()

    internal val sourceManager: SourceManager = Injekt.get()
    internal val trackManager: TrackManager = Injekt.get()
    protected val preferences: PreferencesHelper = Injekt.get()
    private val getFavorites: GetFavorites = Injekt.get()

    // SY -->
    private val getMergedManga: GetMergedManga = Injekt.get()
    protected val customMangaManager: CustomMangaManager = Injekt.get()
    private val insertFlatMetadata: InsertFlatMetadata = Injekt.get()
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get()
    // SY <--

    abstract suspend fun createBackup(uri: Uri, flags: Int, isAutoBackup: Boolean): String

    /**
     * Returns manga
     *
     * @return [Manga], null if not found
     */
    internal suspend fun getMangaFromDatabase(url: String, source: Long): DbManga? {
        return handler.awaitOneOrNull { mangasQueries.getMangaByUrlAndSource(url, source) }
    }

    /**
     * Returns list containing manga from library
     *
     * @return [Manga] from library
     */
    protected suspend fun getFavoriteManga(): List<DomainManga> {
        return getFavorites.await()
    }

    // SY -->
    protected suspend fun getReadManga(): List<DomainManga> {
        return handler.awaitList { mangasQueries.getReadMangaNotInLibrary(mangaMapper) }
    }

    /**
     * Returns list containing merged manga that are possibly not in the library
     *
     * @return merged [Manga] that are possibly not in the library
     */
    protected suspend fun getMergedManga(): List<DomainManga> {
        return getMergedManga.await()
    }

    protected suspend fun getFlatMetadata(mangaId: Long) = getFlatMetadataById.await(mangaId)

    protected suspend fun insertFlatMetadata(flatMetadata: FlatMetadata) = insertFlatMetadata.await(flatMetadata)
    // SY <--

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    internal suspend fun insertManga(manga: Manga): Long {
        return handler.awaitOne(true) {
            mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.getGenres(),
                title = manga.title,
                status = manga.status.toLong(),
                thumbnailUrl = manga.thumbnail_url,
                favorite = manga.favorite,
                lastUpdate = manga.last_update,
                nextUpdate = 0L,
                initialized = manga.initialized,
                viewerFlags = manga.viewer_flags.toLong(),
                chapterFlags = manga.chapter_flags.toLong(),
                coverLastModified = manga.cover_last_modified,
                dateAdded = manga.date_added,
            )
            mangasQueries.selectLastInsertedRowId()
        }
    }

    internal suspend fun updateManga(manga: Manga): Long {
        handler.await(true) {
            mangasQueries.update(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                title = manga.title,
                status = manga.status.toLong(),
                thumbnailUrl = manga.thumbnail_url,
                favorite = manga.favorite.toLong(),
                lastUpdate = manga.last_update,
                initialized = manga.initialized.toLong(),
                viewer = manga.viewer_flags.toLong(),
                chapterFlags = manga.chapter_flags.toLong(),
                coverLastModified = manga.cover_last_modified,
                dateAdded = manga.date_added,
                mangaId = manga.id!!,
                filteredScanlators = manga.filtered_scanlators,
            )
        }
        return manga.id!!
    }

    /**
     * Inserts list of chapters
     */
    protected suspend fun insertChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.insert(
                    chapter.manga_id!!,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read,
                    chapter.bookmark,
                    chapter.last_page_read.toLong(),
                    chapter.chapter_number,
                    chapter.source_order.toLong(),
                    chapter.date_fetch,
                    chapter.date_upload,
                )
            }
        }
    }

    /**
     * Updates a list of chapters
     */
    protected suspend fun updateChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.update(
                    chapter.manga_id!!,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read.toLong(),
                    chapter.bookmark.toLong(),
                    chapter.last_page_read.toLong(),
                    chapter.chapter_number.toDouble(),
                    chapter.source_order.toLong(),
                    chapter.date_fetch,
                    chapter.date_upload,
                    chapter.id!!,
                )
            }
        }
    }

    /**
     * Updates a list of chapters with known database ids
     */
    protected suspend fun updateKnownChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.update(
                    mangaId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = chapter.read.toLong(),
                    bookmark = chapter.bookmark.toLong(),
                    lastPageRead = chapter.last_page_read.toLong(),
                    chapterNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    chapterId = chapter.id!!,
                )
            }
        }
    }

    /**
     * Return number of backups.
     *
     * @return number of backups selected by user
     */
    protected fun numberOfBackups(): Int = preferences.numberOfBackups().get()
}
