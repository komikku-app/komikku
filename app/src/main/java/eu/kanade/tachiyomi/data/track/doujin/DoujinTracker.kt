package eu.kanade.tachiyomi.data.track.doujin

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.delay
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.SocketTimeoutException
import kotlin.math.absoluteValue
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class DoujinTracker(id: Long) : BaseTracker(id, "Doujin Tracker"), DeletableTracker {

    companion object {
        const val PLAN_TO_READ = 1L
        const val READING = 2L
        const val COMPLETED = 3L
        const val ON_HOLD = 4L
        const val DROPPED = 5L

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
            .toImmutableList()
    }

    private val api by lazy { DoujinTrackerApi(client) }
    private val getManga: GetManga by injectLazy()
    private val json: Json by injectLazy()

    override fun getLogo() = R.drawable.brand_suwayomi

    override fun getStatusList(): List<Long> = listOf(PLAN_TO_READ, READING, COMPLETED, ON_HOLD, DROPPED)

    override fun getStatus(status: Long): StringResource? = when (status) {
        PLAN_TO_READ -> MR.strings.plan_to_read
        READING -> MR.strings.reading
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun displayScore(track: DomainTrack): String = track.score.toInt().toString()

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETED && didReadChapter) {
            track.status = if (
                track.total_chapters > 0 &&
                track.last_chapter_read.toLong() >= track.total_chapters
            ) {
                COMPLETED
            } else {
                READING
            }
        }

        val titleId = track.tracking_url.titleIdFromUrl()

        if (titleId == null) {
            // Not yet created remotely; create then continue with local track info.
            val manga = getManga.await(track.manga_id)
            val created = withAuthorizedToken { token ->
                api.addTitle(
                    token,
                    buildAddTitleRequest(track, mapStatusToApi(track.status), manga),
                )
            }
            track.remote_id = created.id.hashCode().toLong().absoluteValue
            track.tracking_url = trackUrl(created.id)
            return track
        }

        withAuthorizedToken { token ->
            api.updateTitle(
                token = token,
                titleId = titleId,
                requestBody = UpdateTitleRequest(
                    status = mapStatusToApi(track.status),
                    progress = track.last_chapter_read.toInt(),
                ),
            )
        }
        return track
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        // If user selected an existing remote entry, reuse it instead of creating duplicates.
        val existingId = track.tracking_url.titleIdFromUrl()
        if (existingId != null) {
            return refresh(track)
        }

        val status = if (hasReadChapters) READING else PLAN_TO_READ
        val manga = getManga.await(track.manga_id)
        val created = withAuthorizedToken { token ->
            api.addTitle(
                token,
                buildAddTitleRequest(track, mapStatusToApi(status), manga),
            )
        }

        track.status = status
        track.remote_id = created.id.hashCode().toLong().absoluteValue
        track.tracking_url = trackUrl(created.id)
        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.isBlank()) return emptyList()

        return withAuthorizedToken { token ->
            api.getTitles(token).titles
                .filter { it.title.contains(query, ignoreCase = true) }
                .map { remote ->
                    TrackSearch.create(id).apply {
                        title = remote.title
                        remote_id = remote.id.hashCode().toLong().absoluteValue
                        tracking_url = trackUrl(remote.id)
                        total_chapters = (remote.totalChapters ?: 0).toLong()
                        last_chapter_read = remote.progress.toDouble()
                        status = mapStatusFromApi(remote.status)
                        cover_url = remote.coverUrl.orEmpty()
                        authors = listOfNotNull(remote.author)
                        artists = listOfNotNull(remote.circle)
                        publishing_type = remote.event.orEmpty()
                        publishing_status = remote.parody.orEmpty()
                        summary = remote.description
                            ?: "Synced entry from Doujin Tracker"
                    }
                }
        }
    }

    override suspend fun refresh(track: Track): Track {
        val titleId = track.tracking_url.titleIdFromUrl() ?: return track
        val remote = withAuthorizedToken { token ->
            api.getTitles(token).titles.firstOrNull { it.id == titleId }
        } ?: return track

        track.title = remote.title
        track.status = mapStatusFromApi(remote.status)
        track.last_chapter_read = remote.progress.toDouble()
        track.total_chapters = (remote.totalChapters ?: 0).toLong()
        track.remote_id = remote.id.hashCode().toLong().absoluteValue
        track.tracking_url = trackUrl(remote.id)
        return track
    }

    override suspend fun login(username: String, password: String) {
        val response = api.login(username, password)
        saveSession(response.userId, response.token, response.refreshToken)
    }

    override suspend fun delete(track: DomainTrack) {
        val titleId = track.remoteUrl.titleIdFromUrl() ?: return
        withAuthorizedToken { token -> api.deleteTitle(token, titleId) }
    }

    override suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata {
        val titleId = track.remoteUrl.titleIdFromUrl() ?: return TrackMangaMetadata()
        val remote = withAuthorizedToken { token ->
            api.getTitles(token).titles.firstOrNull { it.id == titleId }
        }
            ?: return TrackMangaMetadata()

        return TrackMangaMetadata(
            remoteId = titleId.hashCode().toLong().absoluteValue,
            title = remote.title,
            thumbnailUrl = remote.coverUrl,
        )
    }

    override suspend fun searchById(id: String): TrackSearch? {
        val remote = withAuthorizedToken { token ->
            api.getTitles(token).titles.firstOrNull { it.id == id }
        } ?: return null
        return TrackSearch.create(this.id).apply {
            title = remote.title
            remote_id = remote.id.hashCode().toLong().absoluteValue
            tracking_url = trackUrl(remote.id)
            total_chapters = (remote.totalChapters ?: 0).toLong()
            last_chapter_read = remote.progress.toDouble()
            status = mapStatusFromApi(remote.status)
            cover_url = remote.coverUrl.orEmpty()
            authors = listOfNotNull(remote.author)
            artists = listOfNotNull(remote.circle)
            publishing_type = remote.event.orEmpty()
            publishing_status = remote.parody.orEmpty()
            summary = remote.description.orEmpty()
        }
    }

    override fun hasNotStartedReading(status: Long): Boolean = status == PLAN_TO_READ

    private fun mapStatusToApi(status: Long): String = when (status) {
        PLAN_TO_READ -> "plan_to_read"
        READING -> "reading"
        COMPLETED -> "completed"
        ON_HOLD -> "on_hold"
        DROPPED -> "dropped"
        else -> "plan_to_read"
    }

    private fun mapStatusFromApi(status: String): Long = when (status.lowercase()) {
        "plan_to_read", "planned" -> PLAN_TO_READ
        "reading" -> READING
        "completed" -> COMPLETED
        "on_hold" -> ON_HOLD
        "dropped" -> DROPPED
        else -> PLAN_TO_READ
    }

    private fun trackUrl(titleId: String): String = "${DoujinTrackerApi.baseUrl}/tracker/$titleId"

    private fun String?.titleIdFromUrl(): String? {
        if (this.isNullOrBlank()) return null
        if (!startsWith("${DoujinTrackerApi.baseUrl}/tracker/")) return null
        return substringAfterLast("/").ifBlank { null }
    }

    private fun buildAddTitleRequest(track: Track, status: String, manga: Manga?): AddTitleRequest {
        val genres = manga?.genre
            ?.filter { it.isNotBlank() }
            ?.joinToString(", ")
            ?.takeIf { it.isNotBlank() }

        return AddTitleRequest(
            title = track.title,
            author = manga?.author,
            circle = manga?.artist,
            parody = genres,
            description = manga?.description,
            coverUrl = manga?.thumbnailUrl,
            status = status,
            progress = track.last_chapter_read.toInt(),
            sourceId = manga?.url,
        )
    }

    private suspend fun <T> withAuthorizedToken(block: suspend (token: String) -> T): T {
        val session = getSession()

        return try {
            withTimeoutRetry { block(session.accessToken) }
        } catch (error: Throwable) {
            if (!error.isUnauthorized()) throw error

            val refreshToken = session.refreshToken ?: throw error
            val refreshed = api.refreshSession(refreshToken)
            saveSession(
                userId = if (refreshed.userId.isNotBlank()) refreshed.userId else getUsername(),
                accessToken = refreshed.token,
                refreshToken = refreshed.refreshToken ?: refreshToken,
            )
            withTimeoutRetry { block(refreshed.token) }
        }
    }

    private suspend fun <T> withTimeoutRetry(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: Throwable) {
            if (!error.isTimeoutLike()) throw error
            delay(1200)
            block()
        }
    }

    private fun getSession(): StoredSession {
        val raw = getPassword()
        return try {
            json.decodeFromString<StoredSession>(raw)
        } catch (_: Throwable) {
            StoredSession(accessToken = raw, refreshToken = null)
        }
    }

    private fun saveSession(userId: String, accessToken: String, refreshToken: String?) {
        val payload = json.encodeToString(StoredSession(accessToken, refreshToken))
        saveCredentials(userId, payload)
    }

    private fun Throwable.isUnauthorized(): Boolean {
        val text = message?.lowercase().orEmpty()
        return text.contains("401") || text.contains("unauthorized") || text.contains("invalid token")
    }

    private fun Throwable.isTimeoutLike(): Boolean {
        if (this is SocketTimeoutException) return true
        val text = message?.lowercase().orEmpty()
        return text.contains("timeout") || text.contains("timed out")
    }
}

@Serializable
private data class StoredSession(
    val accessToken: String,
    val refreshToken: String? = null,
)
