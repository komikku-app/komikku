package tachiyomi.domain.track.interactor

import tachiyomi.domain.track.model.Track

class IsTrackUnfollowed {

    fun await(track: Track) =
        // TrackManager.MDLIST
        track.trackerId == 60L &&
            // FollowStatus.UNFOLLOWED
            track.status == 0L
}
