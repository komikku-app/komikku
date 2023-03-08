package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.repository.HistoryRepository

class GetHistoryByMangaId(
    private val repository: HistoryRepository,
) {

    suspend fun await(mangaId: Long): List<History> {
        return repository.getByMangaId(mangaId)
    }
}
