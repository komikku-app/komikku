package eu.kanade.domain.manga.interactor

// KMK -->
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
// KMK <--

class SetMangaViewerFlags(
    private val mangaRepository: MangaRepository,
) {

    // KMK -->
    suspend fun awaitSetReadingMode(id: Long, flag: Long): Manga? {
        return awaitSetFlag(id, flag, ReadingMode.MASK.toLong())
    }

    suspend fun awaitSetOrientation(id: Long, flag: Long): Manga? {
        return awaitSetFlag(id, flag, ReaderOrientation.MASK.toLong())
    }

    private suspend fun awaitSetFlag(id: Long, flag: Long, mask: Long): Manga? {
        val manga = mangaRepository.getMangaById(id)
        val updatedManga = manga.copy(viewerFlags = manga.viewerFlags.setFlag(flag, mask))
        return if (mangaRepository.update(MangaUpdate(id = id, viewerFlags = updatedManga.viewerFlags))) {
            updatedManga
        } else {
            null
        }
    }
    // KMK <--

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
