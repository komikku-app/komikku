package tachiyomi.domain.track.interactor

import tachiyomi.domain.track.model.Track

class IsTrackUnfollowed {

    fun await(track: Track) =
        track.trackerId == 60L && // TrackManager.MDLIST
            track.status == 0L // FollowStatus.UNFOLLOWED
}
