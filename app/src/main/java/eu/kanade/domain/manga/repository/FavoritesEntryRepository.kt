package eu.kanade.domain.manga.repository

import exh.favorites.sql.models.FavoriteEntry

interface FavoritesEntryRepository {
    suspend fun deleteAll()

    suspend fun insertAll(favoriteEntries: List<FavoriteEntry>)

    suspend fun selectAll(): List<FavoriteEntry>
}
