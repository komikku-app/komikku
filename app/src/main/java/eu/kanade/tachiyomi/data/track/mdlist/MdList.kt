package eu.kanade.tachiyomi.data.track.mdlist

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.metadata.metadata.base.insertFlatMetadataAsync
import exh.util.executeOnIO
import exh.util.floor
import uy.kohesive.injekt.injectLazy

class MdList(private val context: Context, id: Int) : TrackService(id) {

    private val mdex by lazy { MdUtil.getEnabledMangaDex() }
    private val db: DatabaseHelper by injectLazy()

    @StringRes
    override fun nameRes(): Int = R.string.mdlist

    override fun getLogo(): Int {
        return R.drawable.ic_tracker_mangadex_logo
    }

    override fun getLogoColor(): Int {
        return Color.rgb(43, 48, 53)
    }

    override fun getStatusList(): List<Int> {
        return FollowStatus.values().map { it.int }
    }

    override fun getStatus(status: Int): String =
        context.resources.getStringArray(R.array.md_follows_options).asList()[status]

    override fun getScoreList() = IntRange(0, 10).map(Int::toString)

    override fun displayScore(track: Track) = track.score.toInt().toString()

    override suspend fun add(track: Track): Track = update(track)

    override suspend fun update(track: Track): Track {
        val mdex = mdex ?: throw Exception("Mangadex not enabled")
        val mangaMetadata = db.getFlatMetadataForManga(track.manga_id).executeOnIO()
            ?.raise<MangaDexSearchMetadata>()
            ?: throw Exception("Invalid manga metadata")
        val followStatus = FollowStatus.fromInt(track.status)

        // allow follow status to update
        if (mangaMetadata.follow_status != followStatus.int) {
            mdex.updateFollowStatus(MdUtil.getMangaId(track.tracking_url), followStatus)
            mangaMetadata.follow_status = followStatus.int
            db.insertFlatMetadataAsync(mangaMetadata.flatten()).await()
        }

        if (track.score.toInt() > 0) {
            mdex.updateRating(track)
        }

        // mangadex wont update chapters if manga is not follows this prevents unneeded network call

        if (followStatus != FollowStatus.UNFOLLOWED) {
            if (track.total_chapters != 0 && track.last_chapter_read == track.total_chapters) {
                track.status = FollowStatus.COMPLETED.int
                mdex.updateFollowStatus(MdUtil.getMangaId(track.tracking_url), FollowStatus.COMPLETED)
            }
            if (followStatus == FollowStatus.PLAN_TO_READ && track.last_chapter_read > 0) {
                val newFollowStatus = FollowStatus.READING
                track.status = FollowStatus.READING.int
                mdex.updateFollowStatus(MdUtil.getMangaId(track.tracking_url), newFollowStatus)
                mangaMetadata.follow_status = newFollowStatus.int
                db.insertFlatMetadataAsync(mangaMetadata.flatten()).await()
            }

            mdex.updateReadingProgress(track)
        } else if (track.last_chapter_read != 0) {
            // When followStatus has been changed to unfollowed 0 out read chapters since dex does
            track.last_chapter_read = 0
        }
        return track
    }

    override fun getCompletionStatus(): Int = FollowStatus.COMPLETED.int

    override suspend fun bind(track: Track): Track = update(refresh(track))

    override suspend fun refresh(track: Track): Track {
        val mdex = mdex ?: throw Exception("Mangadex not enabled")
        val remoteTrack = mdex.fetchTrackingInfo(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        if (track.total_chapters == 0 && db.getManga(track.manga_id).executeOnIO()?.status == SManga.COMPLETED) {
            track.total_chapters = db.getChapters(track.manga_id).executeOnIO().maxOfOrNull { it.chapter_number }?.floor() ?: 0
        }
        return track
    }

    fun createInitialTracker(manga: Manga): Track {
        val track = Track.create(TrackManager.MDLIST)
        track.manga_id = manga.id!!
        track.status = FollowStatus.UNFOLLOWED.int
        track.tracking_url = MdUtil.baseUrl + manga.url
        track.title = manga.title
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> = throw Exception("not used")

    override suspend fun login(username: String, password: String): Unit = throw Exception("not used")
}
