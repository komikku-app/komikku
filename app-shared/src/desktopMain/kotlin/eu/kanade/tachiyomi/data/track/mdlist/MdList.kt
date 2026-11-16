package eu.kanade.tachiyomi.data.track.mdlist

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import exh.md.utils.FollowStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.domain.track.model.Track as DomainTrack

/**
 * KMK: desktop stand-in for the SY MangaDex tracker (Android: `MdList`, which logs in and syncs through
 * the built-in MangaDex delegated source; that source is not on desktop). Same id / name / statuses so
 * restored MDList tracks display; never logged in, every remote call fails with a clear message.
 */
class MdList(id: Long) : BaseTracker(id, "MDList") {

    override val supportsReadingDates: Boolean = false

    override fun getLogo() = R.drawable.brand_mangadex

    override fun getStatusList(): List<Long> = FollowStatus.entries.map { it.long }

    override fun getStatus(status: Long): StringResource? = when (status) {
        FollowStatus.UNFOLLOWED.long -> SYMR.strings.md_follows_unfollowed
        FollowStatus.READING.long -> MR.strings.reading
        FollowStatus.COMPLETED.long -> MR.strings.completed
        FollowStatus.ON_HOLD.long -> MR.strings.on_hold
        FollowStatus.PLAN_TO_READ.long -> MR.strings.plan_to_read
        FollowStatus.DROPPED.long -> MR.strings.dropped
        FollowStatus.RE_READING.long -> MR.strings.repeating
        else -> null
    }

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun displayScore(track: DomainTrack) = track.score.toInt().toString()

    override fun getCompletionStatus(): Long = FollowStatus.COMPLETED.long

    override fun getReadingStatus(): Long = FollowStatus.READING.long

    override fun getRereadingStatus(): Long = FollowStatus.RE_READING.long

    override fun hasNotStartedReading(status: Long): Boolean = status == FollowStatus.PLAN_TO_READ.long

    override suspend fun update(track: Track, didReadChapter: Boolean): Track = unsupported()

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track = unsupported()

    override suspend fun refresh(track: Track): Track = unsupported()

    override suspend fun search(query: String): List<TrackSearch> = unsupported()

    override suspend fun login(username: String, password: String): Unit = unsupported()

    override suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata = unsupported()

    override val isLoggedIn: Boolean get() = false

    override val isLoggedInFlow: Flow<Boolean> = flowOf(false)

    private fun unsupported(): Nothing = throw UnsupportedOperationException("MDList needs the MangaDex source, which is not available on desktop")

    companion object {
        private val SCORE_LIST = persistentListOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
    }
}
