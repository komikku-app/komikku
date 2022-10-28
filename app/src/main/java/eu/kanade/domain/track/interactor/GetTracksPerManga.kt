package eu.kanade.domain.track.interactor

import eu.kanade.domain.track.repository.TrackRepository
import eu.kanade.tachiyomi.data.track.TrackManager
import exh.md.utils.FollowStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetTracksPerManga(
    private val trackRepository: TrackRepository,
) {

    fun subscribe(): Flow<Map<Long, List<Long>>> {
        return trackRepository.getTracksAsFlow().map { tracks ->
            tracks
                .groupBy { it.mangaId }
                .mapValues { entry ->
                    entry.value
                        // SY -->
                        .filterNot { it.syncId == TrackManager.MDLIST && it.status == FollowStatus.UNFOLLOWED.int.toLong() }
                        // SY <--
                        .map { it.syncId }
                }
        }
    }
}
