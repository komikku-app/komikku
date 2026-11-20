package eu.kanade.tachiyomi.data.cover

// KMK -->
import android.app.Application
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.notify
import exh.log.xLogW
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.libraryUpdateError.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.kmk.KMR
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import kotlin.math.floor

/**
 * Replaces the cover of a library manga with the latest volume cover in the chosen language (default Japanese)
 * from MangaBaka (https://mangabaka.org). Covers are written to the existing custom cover slot, so
 * the source extension is not touched. A marker file next to the cover records what this class
 * wrote, so a cover the user set by hand is never overwritten.
 *
 * Cover data courtesy of MangaBaka and its underlying providers (CC BY-NC-SA 4.0).
 */
class MangaBakaCoverUpdater(
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val application: Application = Injekt.get(),
    private val insertLibraryUpdateErrors: InsertLibraryUpdateErrors = Injekt.get(),
    private val insertLibraryUpdateErrorMessages: InsertLibraryUpdateErrorMessages = Injekt.get(),
) {

    private val client get() = networkHelper.client

    /** Tracker ID to MangaBaka source name, in order of preference. */
    private val sources = listOf(
        trackerManager.aniList.id to "anilist",
        trackerManager.myAnimeList.id to "my-anime-list",
        trackerManager.kitsu.id to "kitsu",
        trackerManager.mangaUpdates.id to "manga-updates",
    )
    private val json = Json { ignoreUnknownKeys = true }
    private var lastApiCall = 0L

    /**
     * Updates covers for every title in the list, one at a time to stay inside MangaBaka's rate limit.
     * Runs independently of the chapter-update restrictions (completed, unread, release window).
     * With [force] the daily wait between checks is ignored, for refreshes the user asked for.
     * [onProgress] is called at most every [PROGRESS_INTERVAL_MS] with the title being checked.
     *
     * Failures are written to the library update error list, like failed chapter updates, except for
     * titles in [keepErrorsFor] (titles whose chapter update already failed keep that error).
     * A summary notification follows a forced pass, or any pass that changed or failed something.
     * @return the number of titles that failed.
     */
    suspend fun updateAll(
        mangaList: List<Manga>,
        force: Boolean = false,
        keepErrorsFor: Set<Long> = emptySet(),
        onProgress: suspend (manga: Manga, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): Int = passLock.withLock {
        if (!libraryPreferences.mangaBakaCovers().get()) return@withLock 0
        var lastProgress = 0L
        var updated = 0
        var unchanged = 0
        var noCovers = 0
        var notTracked = 0
        var userCovers = 0
        var failed = 0
        mangaList.forEachIndexed { done, manga ->
            currentCoroutineContext().ensureActive()
            if (now() - lastProgress >= PROGRESS_INTERVAL_MS) {
                lastProgress = now()
                onProgress(manga, done, mangaList.size)
            }
            when (val outcome = update(manga, force)) {
                is Outcome.Updated -> updated++
                Outcome.Unchanged -> unchanged++
                Outcome.NoCovers -> noCovers++
                Outcome.NotTracked -> notTracked++
                Outcome.UserCover -> userCovers++
                is Outcome.Failed -> {
                    failed++
                    if (manga.id !in keepErrorsFor) writeError(manga, outcome.reason)
                }
                Outcome.CheckedRecently, Outcome.Skipped -> Unit
            }
        }
        if (force || updated > 0 || failed > 0) {
            showResult(
                KMR.strings.mangabaka_covers_result_title,
                application.stringResource(
                    KMR.strings.mangabaka_covers_result,
                    updated,
                    unchanged,
                    noCovers,
                    notTracked,
                    userCovers,
                    failed,
                ),
            )
        }
        failed
    }

    /**
     * Puts the source's own cover back for every title whose cover this class wrote, for when the
     * feature is turned off. Covers the user set by hand stay. Markers are removed too, so turning the
     * feature back on checks every title afresh.
     */
    suspend fun restoreSourceCovers(
        mangaList: List<Manga>,
        onProgress: suspend (manga: Manga, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ) = passLock.withLock {
        withIOContext { restoreAll(mangaList, onProgress) }
    }

    private suspend fun restoreAll(
        mangaList: List<Manga>,
        onProgress: suspend (manga: Manga, done: Int, total: Int) -> Unit,
    ) {
        var lastProgress = 0L
        var restored = 0
        mangaList.forEachIndexed { done, manga ->
            currentCoroutineContext().ensureActive()
            if (now() - lastProgress >= PROGRESS_INTERVAL_MS) {
                lastProgress = now()
                onProgress(manga, done, mangaList.size)
            }
            val coverFile = coverCache.getCustomCoverFile(manga.id)
            val markerFile = File(coverFile.parentFile, coverFile.name + ".mb")
            val marker = readMarker(markerFile) ?: return@forEachIndexed
            if (coverFile.exists() && marker.owns(coverFile.lastModified())) {
                coverCache.deleteCustomCover(manga.id)
                updateManga.awaitUpdateCoverLastModified(manga.id)
                restored++
            }
            markerFile.delete()
        }
        showResult(
            KMR.strings.mangabaka_covers_restored_title,
            application.stringResource(KMR.strings.mangabaka_covers_restored, restored),
        )
    }

    private fun showResult(title: StringResource, text: String) {
        application.notify(NOTIFICATION_ID, Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setSmallIcon(R.drawable.ic_komikku)
            setContentTitle(application.stringResource(title))
            setContentText(text)
            setStyle(NotificationCompat.BigTextStyle().bigText(text))
            setAutoCancel(true)
        }
    }

    /** Never throws (except cancellation); any failure leaves the current cover untouched. */
    suspend fun update(manga: Manga, force: Boolean = false): Outcome {
        if (!libraryPreferences.mangaBakaCovers().get() || manga.isLocal() || !manga.favorite) return Outcome.Skipped
        return try {
            // File and network work, so never on the caller's (possibly main) thread.
            withIOContext { updateInternal(manga, force) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            xLogW("MangaBaka cover update failed for '${manga.title}'", e)
            Outcome.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /** What [update] did with a title, for feedback to the user. */
    sealed interface Outcome {
        data class Updated(val volume: String) : Outcome
        data object Unchanged : Outcome
        data object NoCovers : Outcome
        data object NotTracked : Outcome
        data object UserCover : Outcome
        data object CheckedRecently : Outcome
        data class Failed(val reason: String) : Outcome
        data object Skipped : Outcome
    }

    private suspend fun writeError(manga: Manga, reason: String) {
        val message = application.stringResource(KMR.strings.mangabaka_cover_failed, reason)
        val messageId = insertLibraryUpdateErrorMessages.insert(LibraryUpdateErrorMessage(-1L, message))
        insertLibraryUpdateErrors.upsert(LibraryUpdateError(id = -1L, mangaId = manga.id, messageId = messageId))
    }

    private suspend fun updateInternal(manga: Manga, force: Boolean): Outcome {
        val language = libraryPreferences.mangaBakaCoverLanguage().get()
        val coverFile = coverCache.getCustomCoverFile(manga.id)
        val markerFile = File(coverFile.parentFile, coverFile.name + ".mb")
        val marker = readMarker(markerFile)

        // A custom cover we did not write (or that was changed since) belongs to the user.
        if (coverFile.exists() && marker?.owns(coverFile.lastModified()) != true) return Outcome.UserCover
        // Checked recently in this language (also covers titles MangaBaka has nothing for).
        val recheck = if (manga.status == SManga.COMPLETED.toLong()) RECHECK_COMPLETED_MS else RECHECK_MS
        val lastCheck = marker?.takeIf { it.language == language }?.checkedAt
        if (!force && lastCheck != null && now() - lastCheck < recheck) return Outcome.CheckedRecently

        val tracks = getTracks.await(manga.id)
        val links = sources.mapNotNull { (trackerId, source) ->
            val remoteId = tracks.firstOrNull { it.trackerId == trackerId }?.remoteId?.takeIf { it > 0 }
                ?: return@mapNotNull null
            // MangaBaka identifies MangaUpdates series by the 7-character base-36 form of the numeric ID.
            val id = if (trackerId == trackerManager.mangaUpdates.id) {
                MangaBakaRules.mangaUpdatesId(remoteId)
            } else {
                remoteId.toString()
            }
            "$source/$id"
        }
        if (links.isEmpty()) return Outcome.NotTracked

        // Reuse the series found last time while that tracker link is unchanged, saving a lookup. If it has
        // no cover now (it may have been merged into another series), look the series up again.
        var series = marker?.takeIf { it.seriesId > 0 && it.link in links }?.let { it.seriesId to it.link }
        var latest = series?.let { latestVolumeCover(it.first, language) }
        if (latest == null) {
            // Try each linked tracker in order until MangaBaka knows the series.
            series = links.firstNotNullOfOrNull { link -> resolveSeriesId(link)?.let { it to link } }
            latest = series?.let { latestVolumeCover(it.first, language) }
        }
        if (series == null || latest == null) {
            // No covers in this language: go back to the source's cover, and remember the miss so it is
            // not looked up again on every update.
            if (coverFile.exists()) {
                coverCache.deleteCustomCover(manga.id)
                updateManga.awaitUpdateCoverLastModified(manga.id)
            }
            writeMarker(markerFile, MangaBakaMarker(0, "", now(), 0, language, ""))
            return Outcome.NoCovers
        }
        val (seriesId, link) = series
        val (index, imageUrl) = latest

        // The cover on disk is already this volume in this language.
        if (coverFile.exists() && marker != null && marker.isCover(seriesId, index, language)) {
            writeMarker(markerFile, marker.copy(checkedAt = now(), link = link))
            return Outcome.Unchanged
        }

        val bytes = fetch(imageUrl) ?: throw HttpException(HTTP_NOT_FOUND)
        // Only accept an actual image (not an error page served with HTTP 200).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return Outcome.Failed("not an image")
        // Claim the cover first, so a write cut short is not mistaken for the user's own cover later.
        writeMarker(markerFile, MangaBakaMarker(seriesId, index, 0, MangaBakaMarker.WRITING, language, link))
        coverCache.setCustomCoverToCache(manga, bytes.inputStream())
        val written = MangaBakaMarker(seriesId, index, now(), coverFile.lastModified(), language, link)
        writeMarker(markerFile, written)
        updateManga.awaitUpdateCoverLastModified(manga.id)
        return Outcome.Updated(index)
    }

    /**
     * The active MangaBaka series for a tracker link ("anilist/30642"). The tracked entry decides the work,
     * so a light novel tracked as such gets the novel's covers.
     */
    private suspend fun resolveSeriesId(link: String): Long? {
        val root = getJson("$API/v1/source/$link") ?: return null
        val series = root["data"]?.jsonObject?.get("series")?.jsonArray?.map { it.jsonObject } ?: return null
        val active = series.firstOrNull { it.str("state") == "active" }
        if (active != null) return active.long("id")
        // Follow a merge once.
        val mergedWith = series.firstNotNullOfOrNull { it.long("merged_with") } ?: return null
        val merged = getJson("$API/v1/series/$mergedWith") ?: return mergedWith
        return merged["data"]?.jsonObject?.long("id") ?: mergedWith
    }

    /** Latest front volume cover in [language], across all pages of the series' covers. */
    private suspend fun latestVolumeCover(seriesId: Long, language: String): VolumeCover? {
        var best: VolumeCover? = null
        var page = 1
        while (page <= MAX_PAGES) {
            val root = getJson("$API/v1/series/$seriesId/images?type=volume&language=$language&limit=50&page=$page")
                ?: break
            val items = (root["data"] as? JsonArray)?.map { it.jsonObject } ?: break
            best = listOfNotNull(best, MangaBakaRules.latestFrontCover(items, language)).maxByOrNull { it.rank }
            val next = root["pagination"]?.jsonObject?.get("next")
            if (next == null || next is JsonNull) break
            page++
        }
        return best
    }

    /** Null means "not found" (HTTP 404). Any other failure throws, so it is not remembered as a miss. */
    private suspend fun getJson(url: String): JsonObject? =
        fetch(url)?.let { json.parseToJsonElement(it.decodeToString()).jsonObject }

    /**
     * Downloads [url]. API calls are spaced so a whole-library pass stays under 180 requests per minute.
     * Rate limits (HTTP 429, waiting as long as the server asks), server errors (5xx) and network errors
     * are retried up to [MAX_ATTEMPTS] times with growing waits, so a busy moment does not skip a title.
     * Returns null for HTTP 404 and throws for any other failure.
     */
    private suspend fun fetch(url: String): ByteArray? {
        var attempt = 1
        while (true) {
            if (url.startsWith(API)) {
                // Image downloads come from the CDN and are not rate limited.
                delay(REQUEST_SPACING_MS - (now() - lastApiCall))
                lastApiCall = now()
            }
            val wait = try {
                client.newCall(Request.Builder().url(url).build()).await().use { response ->
                    val code = response.code
                    when {
                        response.isSuccessful -> return response.body.bytes()
                        code == HTTP_NOT_FOUND -> return null
                        code != HTTP_TOO_MANY_REQUESTS && code < HTTP_SERVER_ERROR -> throw HttpException(code)
                        attempt >= MAX_ATTEMPTS -> throw HttpException(code)
                        else -> response.header("Retry-After")?.toLongOrNull()?.times(1000) ?: backoff(attempt)
                    }
                }
            } catch (e: IOException) {
                if (attempt >= MAX_ATTEMPTS) throw e
                backoff(attempt)
            }
            delay(wait.coerceIn(RETRY_MIN_MS, RETRY_MAX_MS))
            attempt++
        }
    }

    /** 10 s, 20 s, 40 s, 80 s. */
    private fun backoff(attempt: Int) = RETRY_MIN_MS shl (attempt - 1)

    private fun now() = System.currentTimeMillis()

    private fun readMarker(file: File): MangaBakaMarker? =
        runCatching { MangaBakaMarker.parse(file.readText()) }.getOrNull()

    private fun writeMarker(file: File, marker: MangaBakaMarker) = file.writeText(marker.serialize())

    private companion object {
        /** One pass at a time: a library update, the refresh button or a language change. */
        val passLock = Mutex()

        const val API = "https://api.mangabaka.org"

        const val RECHECK_MS = 24L * 60 * 60 * 1000
        const val RECHECK_COMPLETED_MS = 7 * RECHECK_MS
        const val MAX_PAGES = 20
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
        const val MAX_ATTEMPTS = 5
        const val RETRY_MIN_MS = 10_000L
        const val RETRY_MAX_MS = 120_000L
        const val REQUEST_SPACING_MS = 350L
        const val PROGRESS_INTERVAL_MS = 500L
        const val NOTIFICATION_ID = -110
    }
}

/** The pure rules behind [MangaBakaCoverUpdater], kept apart so they can be unit tested. */
internal object MangaBakaRules {
    private val VOLUME_LABEL = Regex("""\d+(\.\d+)?""")

    /**
     * Rank of a volume label: "3" for volume 3, or "3.1" for an alternate edition of volume 3, which only wins
     * when volume 3 itself has no cover. Labels that are not numbers ("Box set", "1-3") have no rank.
     * index_numeric is not used, as it does not always follow the label.
     */
    fun volumeRank(label: String): Double? {
        if (!VOLUME_LABEL.matches(label)) return null
        val number = label.toDouble()
        return floor(number) * 2 + if (number == floor(number)) 1 else 0
    }

    /** MangaBaka identifies MangaUpdates series by the 7-character base-36 form of the numeric ID. */
    fun mangaUpdatesId(remoteId: Long) = remoteId.toString(36).padStart(7, '0')

    /** Latest front cover in [language] among [items] (one page of MangaBaka's images API). */
    fun latestFrontCover(items: List<JsonObject>, language: String): VolumeCover? = items.mapNotNull { item ->
        // Front covers in this language only (the query asks for these; this makes sure).
        // Back covers are "volume_back".
        if (item.str("type") != "volume" || !item.str("language").equals(language, ignoreCase = true)) {
            return@mapNotNull null
        }
        val label = item.str("index") ?: return@mapNotNull null
        val rank = volumeRank(label) ?: return@mapNotNull null
        val image = item["image"]?.jsonObject ?: return@mapNotNull null
        val url = image["x350"]?.jsonObject?.str("x2") ?: image["raw"]?.jsonObject?.str("url")
        url?.let { VolumeCover(label, it, rank) }
    }.maxByOrNull { it.rank }
}

internal data class VolumeCover(val label: String, val url: String, val rank: Double)

/**
 * What [MangaBakaCoverUpdater] last did for a title, kept in a small file next to the cover: the cover
 * is volume [index] of series [seriesId] in [language], found through tracker [link] ("anilist/30642").
 * A miss (no covers) has seriesId 0; a cover being written has coverModified [WRITING].
 */
internal data class MangaBakaMarker(
    val seriesId: Long,
    val index: String,
    val checkedAt: Long,
    val coverModified: Long,
    val language: String,
    val link: String,
) {
    fun isCover(seriesId: Long, index: String, language: String) = coverModified != WRITING &&
        this.seriesId == seriesId && this.index == index && this.language == language

    /** The cover on disk (last modified at [coverModified]) is the one written (or being written) here. */
    fun owns(coverModified: Long) = this.coverModified == WRITING || this.coverModified == coverModified

    fun serialize() = "$seriesId|$index|$checkedAt|$coverModified|$language|$link"

    companion object {
        const val WRITING = -1L

        fun parse(text: String): MangaBakaMarker {
            val parts = text.trim().split('|')
            val (seriesId, index, checkedAt, coverModified, language) = parts
            val link = parts.getOrElse(5) { "" }
            return MangaBakaMarker(
                seriesId = seriesId.toLong(),
                index = index,
                checkedAt = checkedAt.toLong(),
                coverModified = coverModified.toLong(),
                language = language,
                link = link,
            )
        }
    }
}

private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
// KMK <--
