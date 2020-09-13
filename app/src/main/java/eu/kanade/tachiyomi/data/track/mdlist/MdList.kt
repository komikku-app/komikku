package eu.kanade.tachiyomi.data.track.mdlist

import android.content.Context
import android.graphics.Color
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.metadata.metadata.base.insertFlatMetadata
import exh.util.asObservable
import exh.util.floor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import rx.Completable
import rx.Observable
import uy.kohesive.injekt.injectLazy

class MdList(private val context: Context, id: Int) : TrackService(id) {

    private val mdex by lazy { MdUtil.getEnabledMangaDex() }
    private val db: DatabaseHelper by injectLazy()

    override val name = "MDList"

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

    override fun add(track: Track): Observable<Track> {
        return update(track)
    }

    override fun update(track: Track): Observable<Track> {
        val mdex = mdex ?: throw Exception("Mangadex not enabled")
        return Observable.defer {
            db.getManga(track.tracking_url.substringAfter(".org"), mdex.id)
                .asRxObservable()
                .map { manga ->
                    val mangaMetadata = db.getFlatMetadataForManga(manga.id!!).executeAsBlocking()?.raise(MangaDexSearchMetadata::class) ?: throw Exception("Invalid manga metadata")
                    val followStatus = FollowStatus.fromInt(track.status)!!

                    // allow follow status to update
                    if (mangaMetadata.follow_status != followStatus.int) {
                        runBlocking { mdex.updateFollowStatus(MdUtil.getMangaId(track.tracking_url), followStatus).collect() }
                        mangaMetadata.follow_status = followStatus.int
                        db.insertFlatMetadata(mangaMetadata.flatten()).await()
                    }

                    if (track.score.toInt() > 0) {
                        runBlocking { mdex.updateRating(track).collect() }
                    }

                    // mangadex wont update chapters if manga is not follows this prevents unneeded network call

                    if (followStatus != FollowStatus.UNFOLLOWED) {
                        if (track.total_chapters != 0 && track.last_chapter_read == track.total_chapters) {
                            track.status = FollowStatus.COMPLETED.int
                        }

                        runBlocking { mdex.updateReadingProgress(track).collect() }
                    } else if (track.last_chapter_read != 0) {
                        // When followStatus has been changed to unfollowed 0 out read chapters since dex does
                        track.last_chapter_read = 0
                    }
                    track
                }
        }
    }

    override fun getCompletionStatus(): Int = FollowStatus.COMPLETED.int

    override fun bind(track: Track): Observable<Track> {
        val mdex = mdex ?: throw Exception("Mangadex not enabled")
        return mdex.fetchTrackingInfo(track.tracking_url).asObservable()
            .doOnNext { remoteTrack ->
                track.copyPersonalFrom(remoteTrack)
                track.total_chapters = if (remoteTrack.total_chapters == 0) {
                    db.getChapters(track.manga_id).executeAsBlocking().maxOfOrNull { it.chapter_number }?.floor() ?: remoteTrack.total_chapters
                } else {
                    remoteTrack.total_chapters
                }
                update(track)
            }
    }

    override fun refresh(track: Track): Observable<Track> {
        val mdex = mdex ?: throw Exception("Mangadex not enabled")
        return mdex.fetchTrackingInfo(track.tracking_url).asObservable()
            .map { remoteTrack ->
                track.copyPersonalFrom(remoteTrack)
                track.total_chapters = if (remoteTrack.total_chapters == 0) {
                    db.getChapters(track.manga_id).executeAsBlocking().maxOfOrNull { it.chapter_number }?.floor() ?: remoteTrack.total_chapters
                } else {
                    remoteTrack.total_chapters
                }
                track
            }
    }

    fun createInitialTracker(manga: Manga): Track {
        val track = Track.create(TrackManager.MDLIST)
        track.manga_id = manga.id!!
        track.status = FollowStatus.UNFOLLOWED.int
        track.tracking_url = MdUtil.baseUrl + manga.url
        track.title = manga.title
        return track
    }

    override fun search(query: String): Observable<List<TrackSearch>> = throw Exception("not used")

    override fun login(username: String, password: String): Completable = throw Exception("not used")
}
