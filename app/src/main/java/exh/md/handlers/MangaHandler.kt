package exh.md.handlers

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.dto.ChapterDataDto
import exh.md.service.MangaDexService
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import exh.md.utils.mdListCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import rx.Observable
import tachiyomi.core.common.util.lang.runAsObservable
import tachiyomi.core.common.util.lang.withIOContext

class MangaHandler(
    private val lang: String,
    private val service: MangaDexService,
    private val apiMangaParser: ApiMangaParser,
) {
    suspend fun getMangaDetails(
        manga: SManga,
        sourceId: Long,
        coverQuality: String,
        tryUsingFirstVolumeCover: Boolean,
        altTitlesInDesc: Boolean,
        finalChapterInDesc: Boolean,
        preferExtensionLangTitle: Boolean,
    ): SManga {
        return coroutineScope {
            val mangaId = MdUtil.getMangaId(manga.url)
            val response = async(Dispatchers.IO) { service.viewManga(mangaId) }
            val simpleChapters = async(Dispatchers.IO) { getSimpleChapters(manga) }
            val statistics =
                async(Dispatchers.IO) {
                    kotlin.runCatching { service.mangasRating(mangaId) }.getOrNull()?.statistics?.get(mangaId)
                }
            val responseData = response.await()
            val coverFileName = if (tryUsingFirstVolumeCover) {
                async(Dispatchers.IO) {
                    service.fetchFirstVolumeCover(responseData)
                }
            } else {
                null
            }
            apiMangaParser.parseToManga(
                manga,
                sourceId,
                responseData,
                simpleChapters.await(),
                statistics.await(),
                coverFileName?.await(),
                coverQuality,
                altTitlesInDesc,
                finalChapterInDesc,
                preferExtensionLangTitle,
            )
        }
    }

    fun fetchMangaDetailsObservable(
        manga: SManga,
        sourceId: Long,
        coverQuality: String,
        tryUsingFirstVolumeCover: Boolean,
        altTitlesInDesc: Boolean,
        finalChapterInDesc: Boolean,
        preferExtensionLangTitle: Boolean,
    ): Observable<SManga> {
        return runAsObservable {
            getMangaDetails(
                manga,
                sourceId,
                coverQuality,
                tryUsingFirstVolumeCover,
                altTitlesInDesc,
                finalChapterInDesc,
                preferExtensionLangTitle,
            )
        }
    }

    fun fetchChapterListObservable(
        manga: SManga,
        blockedGroups: String,
        blockedUploaders: String,
    ): Observable<List<SChapter>> = runAsObservable {
        getChapterList(manga, blockedGroups, blockedUploaders)
    }

    suspend fun getChapterList(manga: SManga, blockedGroups: String, blockedUploaders: String): List<SChapter> {
        return withIOContext {
            val results = mdListCall {
                service.viewChapters(
                    MdUtil.getMangaId(manga.url),
                    lang,
                    it,
                    blockedGroups,
                    blockedUploaders,
                )
            }

            val groupMap = getGroupMap(results)

            apiMangaParser.chapterListParse(results, groupMap)
        }
    }

    private fun getGroupMap(results: List<ChapterDataDto>): Map<String, String> {
        return results
            .flatMap { it.relationships }
            .filter { it.type == MdConstants.Types.scanlator }
            // KMK -->
            .mapNotNull { relationship ->
                relationship.attributes?.name?.let { relationship.id to it }
            }
            .toMap()
        // KMK <--
    }

    suspend fun fetchRandomMangaId(): String {
        return withIOContext {
            service.randomManga().data.id
        }
    }

    suspend fun getMangaFromChapterId(chapterId: String): String? {
        return withIOContext {
            apiMangaParser.chapterParseForMangaId(service.viewChapter(chapterId))
        }
    }

    suspend fun getMangaMetadata(
        track: Track,
        sourceId: Long,
        coverQuality: String,
        tryUsingFirstVolumeCover: Boolean,
        altTitlesInDesc: Boolean,
        finalChapterInDesc: Boolean,
        preferExtensionLangTitle: Boolean,
    ): SManga {
        return withIOContext {
            val mangaId = MdUtil.getMangaId(track.tracking_url)
            val response = service.viewManga(mangaId)
            val coverFileName = if (tryUsingFirstVolumeCover) {
                service.fetchFirstVolumeCover(response)
            } else {
                null
            }
            apiMangaParser.parseToManga(
                SManga.create().apply {
                    url = track.tracking_url
                },
                sourceId,
                response,
                emptyList(),
                null,
                coverFileName,
                coverQuality,
                altTitlesInDesc,
                finalChapterInDesc,
                preferExtensionLangTitle,
            )
        }
    }

    private suspend fun getSimpleChapters(manga: SManga): List<String> {
        return runCatching { service.aggregateChapters(MdUtil.getMangaId(manga.url), lang) }
            .onFailure {
                if (it is CancellationException) throw it
            }
            .map { dto ->
                dto.volumes.values
                    .flatMap { it.chapters.values }
                    .map { it.chapter }
            }
            .getOrElse { emptyList() }
    }
}
