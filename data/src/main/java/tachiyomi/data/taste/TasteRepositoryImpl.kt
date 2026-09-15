package tachiyomi.data.taste

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.TasteRepository

class TasteRepositoryImpl(
    private val handler: DatabaseHandler,
) : TasteRepository {
    override suspend fun getMangaTaste(mangaId: Long): MangaTaste? = handler.awaitOneOrNull {
        manga_tasteQueries.getByMangaId(mangaId, mangaTasteMapper)
    }

    override fun getMangaTasteAsFlow(mangaId: Long): Flow<MangaTaste?> = handler.subscribeToOneOrNull {
        manga_tasteQueries.getByMangaId(mangaId, mangaTasteMapper)
    }

    override suspend fun getMangaTaste(source: Long, url: String): MangaTaste? = handler.awaitOneOrNull {
        manga_tasteQueries.getBySourceUrl(source, url, mangaTasteMapper)
    }

    override fun getMangaTasteAsFlow(source: Long, url: String): Flow<MangaTaste?> = handler.subscribeToOneOrNull {
        manga_tasteQueries.getBySourceUrl(source, url, mangaTasteMapper)
    }

    override suspend fun getAllMangaTastes(): List<MangaTaste> = handler.awaitList {
        manga_tasteQueries.getAll(mangaTasteMapper)
    }

    override fun getAllMangaTastesAsFlow(): Flow<List<MangaTaste>> = handler.subscribeToList {
        manga_tasteQueries.getAll(mangaTasteMapper)
    }

    override suspend fun upsertMangaTaste(taste: MangaTaste) {
        handler.await(inTransaction = true) {
            manga_tasteQueries.deleteBySourceUrl(taste.source, taste.url)
            manga_tasteQueries.upsert(
                mangaId = taste.mangaId,
                source = taste.source,
                url = taste.url,
                title = taste.title,
                rating = taste.rating.toLong(),
                createdAt = taste.createdAt,
                updatedAt = taste.updatedAt,
            )
        }
    }

    override suspend fun deleteMangaTaste(mangaId: Long) {
        handler.await { manga_tasteQueries.delete(mangaId) }
    }

    override suspend fun deleteAllMangaTastes() {
        handler.await { manga_tasteQueries.deleteAll() }
    }
}

private val mangaTasteMapper = {
        mangaId: Long,
        source: Long,
        url: String,
        title: String,
        rating: Long,
        createdAt: Long,
        updatedAt: Long,
    ->
    MangaTaste(
        mangaId = mangaId,
        source = source,
        url = url,
        title = title,
        rating = rating.toInt(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
