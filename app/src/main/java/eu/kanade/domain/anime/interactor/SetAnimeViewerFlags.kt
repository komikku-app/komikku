package eu.kanade.domain.anime.interactor

import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class SetAnimeViewerFlags(
    private val mangaRepository: MangaRepository,
) {

    suspend fun awaitSetReadingMode(id: Long, flag: Long) {
        val anime = mangaRepository.getMangaById(id)
        mangaRepository.update(
            MangaUpdate(
                id = id,
                viewerFlags = anime.viewerFlags.setFlag(flag, ReadingMode.MASK.toLong()),
            ),
        )
    }

    suspend fun awaitSetOrientation(id: Long, flag: Long) {
        val anime = mangaRepository.getMangaById(id)
        mangaRepository.update(
            MangaUpdate(
                id = id,
                viewerFlags = anime.viewerFlags.setFlag(flag, ReaderOrientation.MASK.toLong()),
            ),
        )
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
