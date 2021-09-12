package exh.md.handlers

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.toSManga
import exh.md.dto.SimilarMangaDto
import exh.md.service.MangaDexService
import exh.md.service.SimilarService
import exh.md.utils.MdUtil
import tachiyomi.source.model.MangaInfo

class SimilarHandler(
    private val lang: String,
    private val service: MangaDexService,
    private val similarService: SimilarService
) {

    suspend fun getSimilar(manga: MangaInfo): MangasPage {
        val similarDto = similarService.getSimilarManga(MdUtil.getMangaId(manga.key))
        return similarDtoToMangaListPage(similarDto)
    }

    private suspend fun similarDtoToMangaListPage(
        similarMangaDto: SimilarMangaDto,
    ): MangasPage {
        val ids = similarMangaDto.matches.map {
            it.id
        }

        val mangaList = service.viewMangas(ids).data.map {
            MdUtil.createMangaEntry(it, lang).toSManga()
        }

        return MangasPage(mangaList, false)
    }
}
