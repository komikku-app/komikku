package tachiyomi.data.anime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.library.model.LibraryAnime
import java.time.LocalDate
import java.time.ZoneId

class AnimeRepositoryImpl(
    private val handler: DatabaseHandler,
) : AnimeRepository {

    override suspend fun getAnimeById(id: Long): Anime {
        return handler.awaitOne { animesQueries.getAnimeById(id, AnimeMapper::mapAnime) }
    }

    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Anime> {
        return handler.subscribeToOne { animesQueries.getAnimeById(id, AnimeMapper::mapAnime) }
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Anime? {
        return handler.awaitOneOrNull {
            animesQueries.getAnimeByUrlAndSource(
                url,
                sourceId,
                AnimeMapper::mapAnime,
            )
        }
    }

    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?> {
        return handler.subscribeToOneOrNull {
            animesQueries.getAnimeByUrlAndSource(
                url,
                sourceId,
                AnimeMapper::mapAnime,
            )
        }
    }

    override suspend fun getFavorites(): List<Anime> {
        return handler.awaitList { animesQueries.getFavorites(AnimeMapper::mapAnime) }
    }

    override suspend fun getReadMangaNotInLibrary(): List<Anime> {
        return handler.awaitList { animesQueries.getSeenAnimeNotInLibrary(AnimeMapper::mapAnime) }
    }

    override suspend fun getLibraryManga(): List<LibraryAnime> {
        return handler.awaitListExecutable {
            (handler as AndroidDatabaseHandler).getLibraryQuery()
        }.map(AnimeMapper::mapLibraryView)
        // return handler.awaitList { libraryViewQueries.library(AnimeMapper::mapLibraryAnime) }
    }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryAnime>> {
        return handler.subscribeToList { libraryViewQueries.library(AnimeMapper::mapLibraryAnime) }
            // SY -->
            .map { getLibraryManga() }
        // SY <--
    }

    override fun getFavoritesBySourceId(sourceId: Long): Flow<List<Anime>> {
        return handler.subscribeToList { animesQueries.getFavoriteBySourceId(sourceId, AnimeMapper::mapAnime) }
    }

    override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<Anime> {
        return handler.awaitList {
            animesQueries.getDuplicateLibraryAnime(title, id, AnimeMapper::mapAnime)
        }
    }

    override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Anime>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return handler.subscribeToList {
            animesQueries.getUpcomingAnime(epochMillis, statuses, AnimeMapper::mapAnime)
        }
    }

    override suspend fun resetViewerFlags(): Boolean {
        return try {
            handler.await { animesQueries.resetViewerFlags() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            animes_categoriesQueries.deleteAnimeCategoryByAnimeId(mangaId)
            categoryIds.map { categoryId ->
                animes_categoriesQueries.insert(mangaId, categoryId)
            }
        }
    }

    override suspend fun insert(manga: Anime): Long? {
        return handler.awaitOneOrNullExecutable(inTransaction = true) {
            // SY -->
            if (animesQueries.getIdByUrlAndSource(manga.url, manga.source).executeAsOneOrNull() != null) {
                return@awaitOneOrNullExecutable animesQueries.getIdByUrlAndSource(manga.url, manga.source)
            }
            // SY <--
            animesQueries.insert(
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
                chapterFlags = manga.episodeFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
                version = manga.version,
            )
            animesQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun update(update: AnimeUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAll(animeUpdates: List<AnimeUpdate>): Boolean {
        return try {
            partialUpdate(*animeUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdate(vararg animeUpdates: AnimeUpdate) {
        handler.await(inTransaction = true) {
            animeUpdates.forEach { value ->
                animesQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    genre = value.genre?.let(StringListColumnAdapter::encode),
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    favorite = value.favorite,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    chapterFlags = value.chapterFlags,
                    coverLastModified = value.coverLastModified,
                    dateAdded = value.dateAdded,
                    mangaId = value.id,
                    updateStrategy = value.updateStrategy?.let(UpdateStrategyColumnAdapter::encode),
                    version = value.version,
                    isSyncing = 0,
                )
            }
        }
    }

    // SY -->
    override suspend fun getMangaBySourceId(sourceId: Long): List<Anime> {
        return handler.awaitList { animesQueries.getBySource(sourceId, AnimeMapper::mapAnime) }
    }

    override suspend fun getAll(): List<Anime> {
        return handler.awaitList { animesQueries.getAll(AnimeMapper::mapAnime) }
    }

    override suspend fun deleteManga(mangaId: Long) {
        handler.await { animesQueries.deleteById(mangaId) }
    }

    override suspend fun getReadMangaNotInLibraryView(): List<LibraryAnime> {
        return handler.awaitListExecutable {
            (handler as AndroidDatabaseHandler).getLibraryQuery("M.favorite = 0 AND C.readCount != 0")
        }.map(AnimeMapper::mapLibraryView)
    }
    // SY <--
}
