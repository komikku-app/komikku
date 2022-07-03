package eu.kanade.data.manga

import eu.kanade.data.DatabaseHandler
import eu.kanade.data.exh.favoriteEntryMapper
import eu.kanade.domain.manga.repository.FavoritesEntryRepository
import exh.favorites.sql.models.FavoriteEntry

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
