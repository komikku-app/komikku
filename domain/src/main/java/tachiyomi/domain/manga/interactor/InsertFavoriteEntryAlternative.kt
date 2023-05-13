package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.FavoriteEntryAlternative
import tachiyomi.domain.manga.repository.FavoritesEntryRepository

class InsertFavoriteEntryAlternative(
    private val favoriteEntryRepository: FavoritesEntryRepository,
) {

    suspend fun await(entry: FavoriteEntryAlternative) {
        return favoriteEntryRepository.addAlternative(entry)
    }
}
