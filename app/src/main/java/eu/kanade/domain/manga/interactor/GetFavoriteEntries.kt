package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.FavoritesEntryRepository
import exh.favorites.sql.models.FavoriteEntry

class GetFavoriteEntries(
    private val favoriteEntryRepository: FavoritesEntryRepository,
) {

    suspend fun await(): List<FavoriteEntry> {
        return favoriteEntryRepository.selectAll()
    }
}
