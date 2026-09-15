package tachiyomi.domain.taste.interactor

import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.TasteRepository

class SetMangaTaste(
    private val repository: TasteRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun await(mangaId: Long, source: Long, url: String, title: String, rating: MangaRating) {
        val timestamp = now()
        val existing = repository.getMangaTaste(source, url)
            ?: repository.getMangaTaste(mangaId)
        repository.upsertMangaTaste(
            MangaTaste(
                mangaId = mangaId,
                source = source,
                url = url,
                title = title,
                rating = rating.value,
                createdAt = existing?.createdAt ?: timestamp,
                updatedAt = timestamp,
            ),
        )
    }
}
