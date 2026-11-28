package exh.md.handlers

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.dto.RelationListDto
import exh.md.dto.SimilarMangaDto
import exh.md.service.MangaDexService
import exh.md.service.SimilarService
import exh.md.utils.MangaDexRelation
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import tachiyomi.core.common.util.lang.withIOContext

class SimilarHandler(
    private val lang: String,
    private val service: MangaDexService,
    private val similarService: SimilarService,
) {

    suspend fun getSimilar(manga: SManga): MetadataMangasPage {
        // KMK -->
        val similarDto = withIOContext { nullOn404 { similarService.getSimilarManga(MdUtil.getMangaId(manga.url)) } }
            ?: return emptyPage()
        // KMK <--
        return similarDtoToMangaListPage(similarDto)
    }

    private suspend fun similarDtoToMangaListPage(
        similarMangaDto: SimilarMangaDto,
    ): MetadataMangasPage {
        val ids = similarMangaDto.matches.map {
            it.id
        }

        val mangaList = service.viewMangas(ids).data.map {
            MdUtil.createMangaEntry(it, lang)
        }

        return MetadataMangasPage(
            mangaList,
            false,
            List(mangaList.size) {
                MangaDexSearchMetadata().also { it.relation = MangaDexRelation.SIMILAR }
            },
        )
    }

    suspend fun getRelated(manga: SManga): MetadataMangasPage {
        // KMK -->
        val relatedListDto = withIOContext { nullOn404 { service.relatedManga(MdUtil.getMangaId(manga.url)) } }
            ?: return emptyPage()
        // KMK <--
        return relatedDtoToMangaListPage(relatedListDto)
    }

    private suspend fun relatedDtoToMangaListPage(
        relatedListDto: RelationListDto,
    ): MetadataMangasPage {
        val ids = relatedListDto.data
            .mapNotNull { it.relationships.firstOrNull() }
            .map { it.id }

        val mangaList = service.viewMangas(ids).data.map {
            MdUtil.createMangaEntry(it, lang)
        }

        return MetadataMangasPage(
            mangas = mangaList,
            hasNextPage = false,
            mangasMetadata = mangaList.map { manga ->
                MangaDexSearchMetadata().also {
                    it.relation = relatedListDto.data
                        .firstOrNull { it.relationships.any { it.id == MdUtil.getMangaId(manga.url) } }
                        ?.attributes?.relation?.let(MangaDexRelation::fromDex)
                }
            },
        )
    }

    // KMK -->
    /**
     * Runs a similar/related lookup for one title, returning null on HTTP 404. similarmanga.com answers 404 for
     * titles it has no data for, which is common: it only means there is nothing to show, and must not hide the
     * results of the other lookup. Only the lookup itself is wrapped, so a 404 from the follow-up
     * [MangaDexService.viewMangas] call still propagates.
     */
    private inline fun <T> nullOn404(block: () -> T): T? {
        return try {
            block()
        } catch (e: HttpException) {
            if (e.code != 404) throw e
            null
        }
    }

    private fun emptyPage() = MetadataMangasPage(emptyList(), false, emptyList())
    // KMK <--
}
