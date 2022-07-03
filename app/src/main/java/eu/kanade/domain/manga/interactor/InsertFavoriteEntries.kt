package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.FavoritesEntryRepository
import exh.favorites.sql.models.FavoriteEntry

class InsertFavoriteEntries(
    private val favoriteEntryRepository: FavoritesEntryRepository,
) {

    suspend fun await(entries: List<FavoriteEntry>) {
        return favoriteEntryRepository.insertAll(entries)
    }
}
