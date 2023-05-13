package tachiyomi.domain.manga.repository

import tachiyomi.domain.manga.model.FavoriteEntry
import tachiyomi.domain.manga.model.FavoriteEntryAlternative

interface FavoritesEntryRepository {
    suspend fun deleteAll()

    suspend fun insertAll(favoriteEntries: List<FavoriteEntry>)

    suspend fun selectAll(): List<FavoriteEntry>

    suspend fun addAlternative(favoriteEntryAlternative: FavoriteEntryAlternative)
}
