package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class NetworkToLocalManga(
    private val mangaRepository: MangaRepository,
) {

    suspend operator fun invoke(manga: Manga, updateInfo: Boolean = true): Manga {
        return invoke(listOf(manga), updateInfo).single()
    }

    suspend operator fun invoke(manga: List<Manga>, updateInfo: Boolean = true): List<Manga> {
        return mangaRepository.insertNetworkManga(manga, updateInfo)
    }
}
