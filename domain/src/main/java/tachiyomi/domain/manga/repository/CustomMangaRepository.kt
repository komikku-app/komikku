package tachiyomi.domain.manga.repository

import tachiyomi.domain.manga.model.CustomMangaInfo

interface CustomMangaRepository {

    fun get(mangaId: Long): CustomMangaInfo?

    fun set(mangaInfo: CustomMangaInfo)

    fun set(mangaInfos: List<CustomMangaInfo>)
}
