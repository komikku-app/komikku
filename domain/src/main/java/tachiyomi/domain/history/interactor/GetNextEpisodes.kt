package tachiyomi.domain.history.interactor

import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.manga.interactor.GetAnime
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Episode
import tachiyomi.domain.chapter.service.getEpisodeSort
import tachiyomi.domain.history.repository.HistoryRepository
import kotlin.math.max

class GetNextEpisodes(
    private val getChaptersByMangaId: GetChaptersByMangaId,
    // SY -->
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId,
    // SY <--
    private val getAnime: GetAnime,
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(onlyUnseen: Boolean = true): List<Episode> {
        val history = historyRepository.getLastHistory() ?: return emptyList()
        return await(history.animeId, history.episodeId, onlyUnseen)
    }

    suspend fun await(animeId: Long, onlyUnseen: Boolean = true): List<Episode> {
        val anime = getAnime.await(animeId) ?: return emptyList()

        // SY -->
        if (anime.source == MERGED_SOURCE_ID) {
            val episodes = getMergedChaptersByMangaId.await(animeId, applyScanlatorFilter = true)
                .sortedWith(getEpisodeSort(anime, sortDescending = false))

            return if (onlyUnseen) {
                episodes.filterNot { it.seen }
            } else {
                episodes
            }
        }
        // SY <--

        val episodes = getChaptersByMangaId.await(animeId, applyScanlatorFilter = true)
            .sortedWith(getEpisodeSort(anime, sortDescending = false))

        return if (onlyUnseen) {
            episodes.filterNot { it.seen }
        } else {
            episodes
        }
    }

    suspend fun await(
        animeId: Long,
        fromEpisodeId: Long,
        onlyUnseen: Boolean = true,
    ): List<Episode> {
        val episodes = await(animeId, onlyUnseen)
        val currEpisodeIndex = episodes.indexOfFirst { it.id == fromEpisodeId }
        val nextEpisodes = episodes.subList(max(0, currEpisodeIndex), episodes.size)

        if (onlyUnseen) {
            return nextEpisodes
        }

        // The "next chapter" is either:
        // - The current chapter if it isn't completely read
        // - The chapters after the current chapter if the current one is completely read
        val fromEpisode = episodes.getOrNull(currEpisodeIndex)
        return if (fromEpisode != null && !fromEpisode.seen) {
            nextEpisodes
        } else {
            nextEpisodes.drop(1)
        }
    }
}
