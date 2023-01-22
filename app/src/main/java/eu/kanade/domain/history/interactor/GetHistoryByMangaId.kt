package eu.kanade.domain.history.interactor

import eu.kanade.domain.history.repository.HistoryRepository
import tachiyomi.domain.history.model.History

class GetHistoryByMangaId(
    private val repository: HistoryRepository,
) {

    suspend fun await(mangaId: Long): List<History> {
        return repository.getByMangaId(mangaId)
    }
}
