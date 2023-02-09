package tachiyomi.data.manga

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.model.FavoriteEntry
import tachiyomi.domain.manga.repository.FavoritesEntryRepository

class FavoritesEntryRepositoryImpl(
    private val handler: DatabaseHandler,
) : FavoritesEntryRepository {
    override suspend fun deleteAll() {
        handler.await { eh_favoritesQueries.deleteAll() }
    }

    override suspend fun insertAll(favoriteEntries: List<FavoriteEntry>) {
        handler.await(true) {
            favoriteEntries.forEach {
                eh_favoritesQueries.insertEhFavorites(it.id, it.title, it.gid, it.token, it.category.toLong())
            }
        }
    }

    override suspend fun selectAll(): List<FavoriteEntry> {
        return handler.awaitList { eh_favoritesQueries.selectAll(favoriteEntryMapper) }
    }
}
