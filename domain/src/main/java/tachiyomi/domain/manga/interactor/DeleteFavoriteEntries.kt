package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.FavoritesEntryRepository

class DeleteFavoriteEntries(
    private val favoriteEntryRepository: FavoritesEntryRepository,
) {

    suspend fun await() {
        return favoriteEntryRepository.deleteAll()
    }
}
