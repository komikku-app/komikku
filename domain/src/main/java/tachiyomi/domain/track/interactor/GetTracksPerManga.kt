package tachiyomi.domain.track.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.track.repository.TrackRepository

class GetTracksPerManga(
    private val trackRepository: TrackRepository,
    private val isTrackUnfollowed: IsTrackUnfollowed,
) {

    fun subscribe(): Flow<Map<Long, List<Long>>> {
        return trackRepository.getTracksAsFlow().map { tracks ->
            tracks
                .groupBy { it.mangaId }
                .mapValues { entry ->
                    entry.value
                        // SY -->
                        .filterNot { isTrackUnfollowed.await(it) }
                        // SY <--
                        .map { it.syncId }
                }
        }
    }
}
