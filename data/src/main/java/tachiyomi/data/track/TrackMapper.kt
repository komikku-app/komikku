package tachiyomi.data.track

import tachiyomi.domain.track.model.Track

object TrackMapper {
    fun mapTrack(
        id: Long,
        mangaId: Long,
        syncId: Long,
        remoteId: Long,
        libraryId: Long?,
        title: String,
        lastVolumeRead: Double,
        totalVolumes: Long,
        lastChapterRead: Double,
        totalChapters: Long,
        status: Long,
        score: Double,
        remoteUrl: String,
        startDate: Long,
        finishDate: Long,
    ): Track = Track(
        id = id,
        mangaId = mangaId,
        trackerId = syncId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastVolumeRead = lastVolumeRead,
        totalVolumes = totalVolumes,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate,
        finishDate = finishDate,
    )
}
