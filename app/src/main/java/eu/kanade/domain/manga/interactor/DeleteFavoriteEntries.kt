package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.repository.FavoritesEntryRepository

class DeleteFavoriteEntries(
    private val favoriteEntryRepository: FavoritesEntryRepository,
) {

    suspend fun await() {
        return favoriteEntryRepository.deleteAll()
    }
}
