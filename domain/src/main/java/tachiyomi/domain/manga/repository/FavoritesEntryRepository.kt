package tachiyomi.domain.manga.repository

import tachiyomi.domain.manga.model.FavoriteEntry

interface FavoritesEntryRepository {
    suspend fun deleteAll()

    suspend fun insertAll(favoriteEntries: List<FavoriteEntry>)

    suspend fun selectAll(): List<FavoriteEntry>
}
