package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.model.MangaRating
import tachiyomi.domain.taste.model.MangaTaste
import tachiyomi.domain.taste.repository.TasteRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TasteRestorer(
    private val getManga: GetManga = Injekt.get(),
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
    private val tasteRepository: TasteRepository = Injekt.get(),
) {
    suspend operator fun invoke(backupTastes: List<BackupMangaTaste>): List<String> = buildList {
        backupTastes.forEach { backup ->
            try {
                restore(backup)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                add("Could not restore rating for ${backup.title}")
            }
        }
    }

    private suspend fun restore(backup: BackupMangaTaste) {
        val rating = MangaRating.fromValue(backup.rating) ?: error("Unknown rating")
        val manga = getManga.await(backup.url, backup.source)
            ?: getManga.await(backup.mangaId)
                ?.takeIf { it.source == backup.source && it.url == backup.url }
            ?: error("Missing manga identity")
        val existing = getMangaTaste.await(manga.source, manga.url)
        if (existing != null && existing.updatedAt >= backup.updatedAt) return

        tasteRepository.upsertMangaTaste(
            MangaTaste(
                mangaId = manga.id,
                source = manga.source,
                url = manga.url,
                title = backup.title,
                rating = rating.value,
                createdAt = existing?.createdAt ?: backup.createdAt,
                updatedAt = backup.updatedAt,
            ),
        )
    }
}
