package exh.md.handlers

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.dto.ChapterDataDto
import exh.md.service.MangaDexService
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import exh.md.utils.mdListCall
import exh.metadata.metadata.MangaDexSearchMetadata
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
    private val followsHandler: FollowsHandler,
) {
    suspend fun getMangaDetails(
        manga: SManga,
        sourceId: Long,
        coverQuality: String,
        tryUsingFirstVolumeCover: Boolean,
        altTitlesInDesc: Boolean,
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
            )
        }
    }

    fun fetchMangaDetailsObservable(manga: SManga, sourceId: Long, coverQuality: String, tryUsingFirstVolumeCover: Boolean, altTitlesInDesc: Boolean): Observable<SManga> {
        return runAsObservable {
            getMangaDetails(manga, sourceId, coverQuality, tryUsingFirstVolumeCover, altTitlesInDesc)
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
        return results.map { chapter -> chapter.relationships }
            .flatten()
            .filter { it.type == MdConstants.Types.scanlator }
            .map { it.id to it.attributes!!.name!! }
            .toMap()
    }

    suspend fun fetchRandomMangaId(): String {
        return withIOContext {
            service.randomManga().data.id
        }
    }

    suspend fun getTrackingInfo(track: Track): Pair<Track, MangaDexSearchMetadata?> {
        return withIOContext {
            /*val metadata = async {
                val mangaUrl = MdUtil.buildMangaUrl(MdUtil.getMangaId(track.tracking_url))
                val manga = MangaInfo(mangaUrl, track.title)
                val response = client.newCall(mangaRequest(manga)).await()
                val metadata = MangaDexSearchMetadata()
                apiMangaParser.parseIntoMetadata(metadata, response, emptyList())
                metadata
            }*/
            val remoteTrack = async {
                followsHandler.fetchTrackingInfo(track.tracking_url)
            }
            remoteTrack.await() to null
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
    ): SManga? {
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
