package tachiyomi.domain.episode.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.repository.EpisodeRepository

class GetEpisode(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(id: Long): Episode? {
        return try {
            episodeRepository.getEpisodeById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun await(url: String, animeId: Long): Episode? {
        return try {
            episodeRepository.getEpisodeByUrlAndAnimeId(url, animeId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }
}
