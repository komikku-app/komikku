package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupMangaTaste
import tachiyomi.domain.taste.interactor.GetMangaTaste
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TasteBackupCreator(
    private val getMangaTaste: GetMangaTaste = Injekt.get(),
) {
    suspend operator fun invoke(): List<BackupMangaTaste> = getMangaTaste.awaitAll().map { taste ->
        BackupMangaTaste(
            mangaId = taste.mangaId,
            source = taste.source,
            url = taste.url,
            title = taste.title,
            rating = taste.rating,
            updatedAt = taste.updatedAt,
            createdAt = taste.createdAt,
        )
    }
}
