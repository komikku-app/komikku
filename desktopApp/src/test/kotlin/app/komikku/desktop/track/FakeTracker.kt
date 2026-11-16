package app.komikku.desktop.track

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import java.util.concurrent.ConcurrentHashMap
import tachiyomi.domain.track.model.Track as DomainTrack

/**
 * An in-test tracker on the borrowed `BaseTracker`: credentials login, a fixed search index and an in-memory
 * "remote" list keyed by remote id. [failUpdates] makes `update` / `refresh` throw (offline service).
 */
class FakeTracker(id: Long = ID) : BaseTracker(id, "Fake tracker") {

    data class Remote(var lastChapterRead: Double = 0.0, var status: Long = READING, var score: Double = 0.0)

    val remote = ConcurrentHashMap<Long, Remote>()

    @Volatile
    var failUpdates = false

    override val supportsReadingDates: Boolean = true

    override fun getLogo() = R.drawable.brand_anilist

    override fun getStatusList(): List<Long> = listOf(READING, COMPLETED, PLAN_TO_READ)

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        PLAN_TO_READ -> MR.strings.plan_to_read
        else -> null
    }

    override fun getReadingStatus(): Long = READING
    override fun getRereadingStatus(): Long = -1
    override fun getCompletionStatus(): Long = COMPLETED
    override fun hasNotStartedReading(status: Long): Boolean = status == PLAN_TO_READ

    override fun getScoreList(): ImmutableList<String> = (0..10).map(Int::toString).toImmutableList()

    override fun displayScore(track: DomainTrack) = track.score.toInt().toString()

    override suspend fun login(username: String, password: String) {
        if (password != PASSWORD) throw Exception("Wrong password")
        saveCredentials(username, password)
    }

    override suspend fun search(query: String): List<TrackSearch> =
        listOf(1L to "$query (manga)", 2L to "$query (novel)").map { (remoteId, title) ->
            TrackSearch.create(id).apply {
                remote_id = remoteId
                this.title = title
                tracking_url = "https://tracker.invalid/manga/$remoteId"
                total_chapters = 10
            }
        }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val entry = remote.getOrPut(track.remote_id) { Remote() }
        track.status = entry.status
        track.last_chapter_read = entry.lastChapterRead
        return track
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (failUpdates) throw Exception("Fake tracker is offline")
        remote[track.remote_id] = Remote(track.last_chapter_read, track.status, track.score)
        return track
    }

    override suspend fun refresh(track: Track): Track {
        if (failUpdates) throw Exception("Fake tracker is offline")
        remote[track.remote_id]?.let {
            track.last_chapter_read = it.lastChapterRead
            track.status = it.status
            track.score = it.score
        }
        return track
    }

    companion object {
        const val ID = 900L
        const val PASSWORD = "secret"
        const val READING = 1L
        const val COMPLETED = 2L
        const val PLAN_TO_READ = 3L
    }
}
