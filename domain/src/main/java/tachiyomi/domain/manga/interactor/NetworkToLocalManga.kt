package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class NetworkToLocalManga(
    private val mangaRepository: MangaRepository,
) {

    suspend operator fun invoke(manga: Manga): Manga {
        return invoke(listOf(manga)).single()
    }

    suspend operator fun invoke(manga: List<Manga>): List<Manga> {
        return mangaRepository.insertNetworkManga(manga)
    }

    // KMK -->
    suspend fun getLocal(manga: Manga): Manga = if (manga.id <= 0) {
        invoke(manga)
    } else {
        manga
    }

    suspend fun getLocal(mangas: List<Manga>): List<Manga> {
        return mangas.map { manga ->
            if (manga.id <= 0) {
                invoke(manga)
            } else {
                manga
            }
        }
    }
    // KMK <--
}
