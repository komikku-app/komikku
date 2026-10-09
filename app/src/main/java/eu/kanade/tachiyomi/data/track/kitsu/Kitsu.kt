package eu.kanade.tachiyomi.data.track.kitsu

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class Kitsu(id: Long) : BaseTracker(id, "Kitsu"), DeletableTracker {

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L

        // KMK -->
        const val SIMPLE = "simple"
        const val REGULAR = "regular"
        const val ADVANCED = "advanced"
        // KMK <--
    }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    private val json: Json by injectLazy()

    private val interceptor by lazy { KitsuInterceptor(this) }

    private val api by lazy { KitsuApi(client, interceptor) }

    // KMK -->
    private val scorePreference = trackPreferences.kitsuScoreType()
    // KMK <--

    override fun getLogo() = R.drawable.brand_kitsu

    override fun getStatusList(): List<Long> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        PLAN_TO_READ -> MR.strings.plan_to_read
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    // KMK -->
    override fun getScoreList(): ImmutableList<String> = kitsuScoreList(scorePreference.get())

    override fun indexToScore(index: Int): Double = kitsuIndexToScore(scorePreference.get(), index)

    override fun displayScore(track: DomainTrack): String =
        kitsuDisplayScore(scorePreference.get(), track.score)
    // KMK <--

    private suspend fun add(track: Track): Track {
        return api.addLibManga(track, getUserId())
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else {
                    track.status = READING
                    if (track.last_chapter_read == 1.0) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateLibManga(track)
    }

    override suspend fun delete(track: DomainTrack) {
        api.removeLibManga(track)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val remoteTrack = api.findLibManga(track, getUserId())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.remote_id = remoteTrack.remote_id
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                track.status = if (hasReadChapters) READING else track.status
            }

            update(track)
        } else {
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) {
        val token = api.login(username, password)
        interceptor.newAuth(token)
        // KMK -->
        val (userId, ratingSystem) = api.getCurrentUser()
        scorePreference.set(ratingSystem)
        // KMK <--
        saveCredentials(username, userId)
    }

    override fun logout() {
        super.logout()
        interceptor.newAuth(null)
    }

    override suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata {
        return api.getMangaMetadata(track)
    }

    private fun getUserId(): String {
        return getPassword()
    }

    fun saveToken(oauth: KitsuOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): KitsuOAuth? {
        return try {
            json.decodeFromString<KitsuOAuth>(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }

    // KMK -->
    override fun hasNotStartedReading(status: Long): Boolean = status == PLAN_TO_READ
    // KMK <--
}
