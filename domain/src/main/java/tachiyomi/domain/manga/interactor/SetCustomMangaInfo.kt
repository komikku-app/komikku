package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository

class SetCustomMangaInfo(
    private val customMangaRepository: CustomMangaRepository,
) {

    fun set(mangaInfo: CustomMangaInfo) = customMangaRepository.set(mangaInfo)

    // KMK -->
    fun setIncognitoMode(mangaId: Long, incognitoMode: Boolean?) {
        val mangaInfo = customMangaRepository.get(mangaId) ?: CustomMangaInfo(mangaId, null)
        set(mangaInfo.copy(incognitoMode = incognitoMode))
    }
    // KMK <--
}
