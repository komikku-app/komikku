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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.ResponseBody
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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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

    /** Does not follow redirects, so a request cannot be sent on to a host other than MangaBaka's. */
    private val client by lazy {
        networkHelper.client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    /** Tracker ID to MangaBaka source name, in order of preference. */
    private val sources = listOf(
        trackerManager.aniList.id to "anilist",
        trackerManager.myAnimeList.id to "my-anime-list",
        trackerManager.kitsu.id to "kitsu",
        trackerManager.mangaUpdates.id to "manga-updates",
    )
    private val json = Json { ignoreUnknownKeys = true }

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
            val markerFile = mangaBakaMarkerFile(coverFile)
            val marker = readMangaBakaMarker(markerFile) ?: return@forEachIndexed
            if (coverFile.exists() && marker.owns(coverFile.lastModified())) {
                // Keep the marker if the cover cannot be deleted, so a later pass can still restore it.
                if (!coverCache.deleteCustomCover(manga.id)) return@forEachIndexed
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
        val markerFile = mangaBakaMarkerFile(coverFile)
        val marker = readMangaBakaMarker(markerFile)

        // A custom cover we did not write (or that was changed since) belongs to the user.
        if (!ownsCover(coverFile, marker)) return Outcome.UserCover
        // Checked recently in this language (also covers titles MangaBaka has nothing for).
        val recheck = if (manga.status == SManga.COMPLETED.toLong()) RECHECK_COMPLETED_MS else RECHECK_MS
        val lastCheck = marker?.takeIf { it.language == language }?.checkedAt
        if (!force && lastCheck != null && now() - lastCheck < recheck) return Outcome.CheckedRecently

        val links = trackerLinks(manga.id)
        if (links.isEmpty()) {
            // No longer tracked (or never was): show the source's cover, and forget the title.
            showSourceCover(manga, coverFile)
            markerFile.delete()
            return Outcome.NotTracked
        }

        val found = findLatestCover(marker, links, language)
        // The lookup took a while: the feature may have been turned off, or the user set a cover, meanwhile.
        if (!libraryPreferences.mangaBakaCovers().get()) return Outcome.Skipped
        val current = readMangaBakaMarker(markerFile)
        if (!ownsCover(coverFile, current)) return Outcome.UserCover

        if (found == null) {
            // No covers in this language: go back to the source's cover, and remember the miss so it is
            // not looked up again on every update.
            showSourceCover(manga, coverFile)
            writeMangaBakaMarker(markerFile, MangaBakaMarker(0, "", now(), 0, language, ""))
            return Outcome.NoCovers
        }
        // The cover on disk is already this volume in this language.
        if (coverFile.exists() && current != null && current.isCover(found.seriesId, found.cover.label, language)) {
            writeMangaBakaMarker(markerFile, current.copy(checkedAt = now(), link = found.link))
            return Outcome.Unchanged
        }
        return writeCover(manga, coverFile, markerFile, found, language)
    }

    /** MangaBaka source links ("anilist/30642") for the title's supported trackers, in order of preference. */
    private suspend fun trackerLinks(mangaId: Long): List<String> {
        val tracks = getTracks.await(mangaId)
        return sources.mapNotNull { (trackerId, source) ->
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
    }

    /** The latest volume cover in [language] of the series behind [links], or null if there is none. */
    private suspend fun findLatestCover(marker: MangaBakaMarker?, links: List<String>, language: String): FoundCover? {
        // Reuse the series found last time while that tracker link is unchanged, saving a lookup. If it has
        // no cover now (it may have been merged into another series), look the series up again.
        marker?.takeIf { it.seriesId > 0 && it.link in links }?.let { known ->
            latestVolumeCover(known.seriesId, language)?.let { return FoundCover(known.link, known.seriesId, it) }
        }
        // Try each linked tracker in order until MangaBaka knows the series.
        val (link, seriesId) = links.firstNotNullOfOrNull { link -> resolveSeriesId(link)?.let { link to it } }
            ?: return null
        return latestVolumeCover(seriesId, language)?.let { FoundCover(link, seriesId, it) }
    }

    private suspend fun writeCover(
        manga: Manga,
        coverFile: File,
        markerFile: File,
        found: FoundCover,
        language: String,
    ): Outcome {
        val bytes = fetch(found.cover.url) ?: throw HttpException(HTTP_NOT_FOUND)
        // Only accept an actual image (not an error page served with HTTP 200).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return Outcome.Failed("not an image")
        // Checked once more right before the cover is replaced, as the download took a while too.
        if (!libraryPreferences.mangaBakaCovers().get()) return Outcome.Skipped
        if (!ownsCover(coverFile, readMangaBakaMarker(markerFile))) return Outcome.UserCover

        val index = found.cover.label
        // Claim the cover first, so a write cut short is not mistaken for the user's own cover later.
        writeMangaBakaMarker(
            markerFile,
            MangaBakaMarker(found.seriesId, index, 0, MangaBakaMarker.WRITING, language, found.link),
        )
        coverCache.setCustomCoverToCache(manga, bytes.inputStream())
        // Refresh the cover's cache key before recording the cover as done, so a failure here is retried.
        check(updateManga.awaitUpdateCoverLastModified(manga.id)) { "could not save the cover" }
        writeMangaBakaMarker(
            markerFile,
            MangaBakaMarker(found.seriesId, index, now(), coverFile.lastModified(), language, found.link),
        )
        return Outcome.Updated(index)
    }

    /** True when there is no custom cover, or the one there was written here and not changed since. */
    private fun ownsCover(coverFile: File, marker: MangaBakaMarker?) =
        !coverFile.exists() || marker?.owns(coverFile.lastModified()) == true

    /** Removes the MangaBaka cover so the source's shows. Throws if it cannot, so the marker is kept. */
    private suspend fun showSourceCover(manga: Manga, coverFile: File) {
        if (!coverFile.exists()) return
        if (!coverCache.deleteCustomCover(manga.id)) throw IOException("could not delete the cover")
        updateManga.awaitUpdateCoverLastModified(manga.id)
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
     * Downloads [url]. API calls are spaced so a whole-library pass stays under 180 requests per minute,
     * across every updater (library update, cover refresh, title screen), as they can overlap.
     * Rate limits (HTTP 429, waiting as long as the server asks), server errors (5xx) and network errors
     * are retried up to [MAX_ATTEMPTS] times with growing waits, so a busy moment does not skip a title.
     * Returns null for HTTP 404 and throws for any other failure. Only MangaBaka's own hosts are contacted,
     * over HTTPS, and a response over [MAX_RESPONSE_BYTES] is refused.
     */
    private suspend fun fetch(url: String): ByteArray? {
        val httpUrl = url.toHttpUrlOrNull()?.takeIf { it.isHttps && MangaBakaRules.isMangaBakaHost(it.host) }
            ?: throw IllegalArgumentException("not a MangaBaka address")
        var attempt = 1
        while (true) {
            if (url.startsWith(API)) {
                // Image downloads come from the CDN and are not rate limited.
                apiSpacing.withLock {
                    delay(REQUEST_SPACING_MS - (now() - lastApiCall))
                    lastApiCall = now()
                }
            }
            val wait = try {
                client.newCall(Request.Builder().url(httpUrl).build()).await().use { response ->
                    val code = response.code
                    when {
                        response.isSuccessful -> return response.body.bytesAtMost(MAX_RESPONSE_BYTES)
                        code == HTTP_NOT_FOUND -> return null
                        code != HTTP_TOO_MANY_REQUESTS && code < HTTP_SERVER_ERROR -> throw HttpException(code)
                        attempt >= MAX_ATTEMPTS -> throw HttpException(code)
                        else -> MangaBakaRules.retryAfterMs(response.header("Retry-After"), now()) ?: backoff(attempt)
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
    private fun backoff(attempt: Int): Long {
        val doublings = attempt - 1
        return RETRY_MIN_MS shl doublings
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        /** One pass at a time: a library update, the refresh button or a language change. */
        val passLock = Mutex()

        /** Spaces API calls across every instance; [lastApiCall] is only read and written under it. */
        val apiSpacing = Mutex()
        var lastApiCall = 0L

        const val API = "https://api.mangabaka.org"

        const val RECHECK_MS = 24L * 60 * 60 * 1000
        const val RECHECK_COMPLETED_MS = 7 * RECHECK_MS
        const val MAX_PAGES = 20
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
        const val MAX_ATTEMPTS = 5
        const val RETRY_MIN_MS = 10_000L

        /** Longest wait between attempts, including a wait the server asks for. */
        const val RETRY_MAX_MS = 5 * 60_000L
        const val MAX_RESPONSE_BYTES = 10L * 1024 * 1024
        const val REQUEST_SPACING_MS = 350L
        const val PROGRESS_INTERVAL_MS = 500L
        const val NOTIFICATION_ID = -110
    }
}

/** The marker file next to a custom cover, recording what [MangaBakaCoverUpdater] wrote there. */
internal fun mangaBakaMarkerFile(coverFile: File) = File(coverFile.parentFile, coverFile.name + ".mb")

internal fun readMangaBakaMarker(file: File): MangaBakaMarker? =
    runCatching { MangaBakaMarker.parse(file.readText()) }.getOrNull()

/** Written aside and renamed into place, so an interrupted write leaves the previous marker intact. */
internal fun writeMangaBakaMarker(file: File, marker: MangaBakaMarker) {
    val temp = File(file.parentFile, file.name + ".tmp")
    temp.writeText(marker.serialize())
    if (!temp.renameTo(file)) {
        temp.delete()
        throw IOException("could not save the cover marker")
    }
}

/** The whole body, refusing anything over [limit] bytes so an oversized response cannot exhaust memory. */
private fun ResponseBody.bytesAtMost(limit: Long): ByteArray {
    val source = source()
    check(contentLength() <= limit && !source.request(limit + 1)) { "response over ${limit / 1024 / 1024} MB" }
    return source.readByteArray()
}

/**
 * After a custom cover was copied from one entry to another (migration), carries MangaBaka's claim on it over too.
 * Without this the copy would count as the user's own cover, and MangaBaka would never update or restore it.
 */
fun copyMangaBakaCoverMarker(fromMangaId: Long, toMangaId: Long, coverCache: CoverCache = Injekt.get()) {
    val fromCover = coverCache.getCustomCoverFile(fromMangaId)
    val toCover = coverCache.getCustomCoverFile(toMangaId)
    val marker = readMangaBakaMarker(mangaBakaMarkerFile(fromCover)) ?: return
    // Only a finished cover that MangaBaka still owns; anything else is left as the user's own.
    if (marker.coverModified == MangaBakaMarker.WRITING || !marker.owns(fromCover.lastModified())) return
    if (!toCover.exists()) return
    runCatching {
        writeMangaBakaMarker(mangaBakaMarkerFile(toCover), marker.copy(coverModified = toCover.lastModified()))
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

    private val MANGABAKA_DOMAINS = listOf("mangabaka.org", "mangabaka.dev")

    /** MangaBaka's API and image hosts: mangabaka.org, mangabaka.dev and their subdomains. */
    fun isMangaBakaHost(host: String) = MANGABAKA_DOMAINS.any { host == it || host.endsWith(".$it") }

    /** The wait a Retry-After header asks for, given in seconds or as an HTTP date; null if absent or unreadable. */
    fun retryAfterMs(value: String?, nowMs: Long): Long? {
        val header = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        header.toLongOrNull()?.let { return it * 1000 }
        val until = runCatching { ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull()
            ?: return null
        return (until.toInstant().toEpochMilli() - nowMs).coerceAtLeast(0)
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

/** A cover found for a title: [cover] of MangaBaka series [seriesId], reached through tracker [link]. */
internal data class FoundCover(val link: String, val seriesId: Long, val cover: VolumeCover)

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
