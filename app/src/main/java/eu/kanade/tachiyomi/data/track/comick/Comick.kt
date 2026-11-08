package eu.kanade.tachiyomi.data.track.comick

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.comick.dto.ComickOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

// KMK -->
class Comick(id: Long) : BaseTracker(id, "Comick"), DeletableTracker {
    private val json: Json by injectLazy()
    private val interceptorDelegate = lazy { ComickInterceptor(this) }
    private val interceptor by interceptorDelegate
    private val api by lazy { ComickApi(id, client, interceptor) }

    override fun getLogo() = R.drawable.brand_comick
    override fun getStatusList() = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)
    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        PLAN_TO_READ -> MR.strings.plan_to_read
        else -> null
    }
    override fun getReadingStatus() = READING
    override fun getRereadingStatus() = -1L
    override fun getCompletionStatus() = COMPLETED
    override fun getScoreList(): ImmutableList<String> = (0..10).map(Int::toString).toImmutableList()
    override fun displayScore(track: DomainTrack) = track.score.toInt().toString()
    override fun hasNotStartedReading(status: Long) = status == PLAN_TO_READ

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETED && track.status != READING && didReadChapter) track.status = READING
        return api.updateLibManga(track)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val remote = api.findLibManga(track)
        return if (remote != null) {
            track.copyPersonalFrom(remote)
            update(track)
        } else {
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            api.addLibManga(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            val hid = query.substringAfter(SEARCH_ID_PREFIX).trim()
            return api.getMangaDetails(hid)?.let(::listOf) ?: emptyList()
        }
        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remote = api.findLibManga(track) ?: error("Could not find manga in Comick library")
        track.copyPersonalFrom(remote)
        return track
    }

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.getAccessToken(code)
            interceptor.setAuth(oauth)
            saveCredentials("Comick", oauth.accessToken)
        } catch (e: Exception) {
            logout()
            throw e
        }
    }

    fun saveToken(oauth: ComickOAuth?) {
        val pref = trackPreferences.trackToken(this)
        if (oauth == null) pref.delete() else pref.set(json.encodeToString(oauth))
    }

    fun restoreToken(): ComickOAuth? = runCatching {
        json.decodeFromString<ComickOAuth>(trackPreferences.trackToken(this).get())
    }.getOrNull()

    fun verifyOAuthState(state: String) = ComickApi.verifyOAuthState(state)

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        if (interceptorDelegate.isInitialized()) interceptor.setAuth(null)
    }

    override suspend fun delete(track: DomainTrack) = api.deleteLibManga(track)

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
        private const val SEARCH_ID_PREFIX = "id:"
    }
}
// KMK <--
