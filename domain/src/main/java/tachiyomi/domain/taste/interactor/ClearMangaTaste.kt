package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.repository.TasteRepository

class ClearMangaTaste(
    private val repository: TasteRepository,
) {
    suspend fun await(mangaId: Long) = repository.deleteMangaTaste(mangaId)
}
