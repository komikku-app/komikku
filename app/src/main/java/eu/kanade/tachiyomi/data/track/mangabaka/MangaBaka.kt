package eu.kanade.tachiyomi.data.track.mangabaka

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

// KMK -->
class MangaBaka(id: Long) : BaseTracker(id, "MangaBaka"), DeletableTracker {
    private val json: Json by injectLazy()
    private val interceptor by lazy { MangaBakaInterceptor(this) }
    private val api by lazy { MangaBakaApi(id, client, interceptor) }
    private val scorePreference = trackPreferences.mangabakaScoreType
    override val supportsReadingDates = true
    override val supportsPrivateTracking = true

    override fun getLogo() = R.drawable.brand_mangabaka
    override fun getStatusList() = listOf(READING, COMPLETED, PAUSED, DROPPED, PLAN_TO_READ, REREADING, CONSIDERING)
    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        PAUSED -> MR.strings.paused
        DROPPED -> MR.strings.dropped
        PLAN_TO_READ -> MR.strings.plan_to_read
        REREADING -> MR.strings.repeating
        CONSIDERING -> KMR.strings.tracker_status_considering
        else -> null
    }
    override fun getReadingStatus() = READING
    override fun getRereadingStatus() = REREADING
    override fun getCompletionStatus() = COMPLETED
    override fun get10PointScore(track: DomainTrack) = track.score / 10.0
    override fun indexToScore(index: Int) = scoreRange().elementAt(index).toDouble()
    override fun getScoreList(): ImmutableList<String> = scoreRange().map(Int::toString).toImmutableList()
    override fun displayScore(track: DomainTrack) = track.score.toInt().toString()
    override fun hasNotStartedReading(status: Long) = status == PLAN_TO_READ || status == CONSIDERING

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (didReadChapter && track.status != COMPLETED && track.status != REREADING) {
            if (track.total_chapters > 0 && track.last_chapter_read.toLong() == track.total_chapters) {
                track.status = COMPLETED
                track.finished_reading_date = System.currentTimeMillis()
            } else {
                track.status = READING
                if (track.last_chapter_read == 1.0 && track.started_reading_date == 0L) track.started_reading_date = System.currentTimeMillis()
            }
        }
        return api.update(track)
    }
    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val remote = api.find(track)
        if (remote != null) {
            track.copyPersonalFrom(remote, copyRemotePrivate = false)
            track.title = remote.title
            track.remote_id = remote.remote_id
            if (track.status != COMPLETED && track.status != REREADING && hasReadChapters) track.status = READING
            return update(track)
        }
        track.status = if (hasReadChapters) READING else PLAN_TO_READ
        track.score = 0.0
        return api.add(track)
    }
    override suspend fun search(query: String): List<TrackSearch> {
        val idQuery = query.takeIf { it.startsWith("id:", true) }?.substringAfter(':')?.trim()?.toLongOrNull()
        return if (idQuery != null) api.searchById(idQuery)?.let(::listOf).orEmpty() else api.search(query)
    }
    override suspend fun refresh(track: Track): Track {
        val remote = api.find(track) ?: error("Could not find manga in MangaBaka library")
        track.copyPersonalFrom(remote)
        track.title = remote.title
        return track
    }
    override suspend fun login(username: String, password: String) = login(password)
    suspend fun login(code: String) {
        try {
            val token = api.exchange(code)
            interceptor.setAuth(token)
            val profile = api.profile()
            scorePreference.set(
                when (profile.ratingSteps) {
                    1 -> STEP_1
                    5 -> STEP_5
                    10 -> STEP_10
                    20 -> STEP_20
                    25 -> STEP_25
                    else -> STEP_1
                },
            )
            saveCredentials(profile.nickname ?: profile.preferredUsername ?: profile.id, token.accessToken)
        } catch (e: Exception) {
            logout()
            throw e
        }
    }
    fun saveToken(token: MangaBakaOAuth?) {
        val pref = trackPreferences.trackToken(this)
        if (token == null) pref.delete() else pref.set(json.encodeToString(token))
    }
    fun restoreToken(): MangaBakaOAuth? = runCatching { json.decodeFromString<MangaBakaOAuth>(trackPreferences.trackToken(this).get()) }.getOrNull()
    fun verifyOAuthState(state: String) = MangaBakaApi.verifyOAuthState(state)
    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }
    override suspend fun delete(track: DomainTrack) = api.delete(track.remoteId)

    private fun scoreRange() = when (scorePreference.get()) {
        STEP_5 -> (0..100 step 5)
        STEP_10 -> (0..100 step 10)
        STEP_20 -> (0..100 step 20)
        STEP_25 -> (0..100 step 25)
        else -> (0..100)
    }
    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val PAUSED = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
        const val REREADING = 6L
        const val CONSIDERING = 7L
        const val STEP_1 = "STEP_1"
        const val STEP_5 = "STEP_5"
        const val STEP_10 = "STEP_10"
        const val STEP_20 = "STEP_20"
        const val STEP_25 = "STEP_25"
    }
}
// KMK <--
