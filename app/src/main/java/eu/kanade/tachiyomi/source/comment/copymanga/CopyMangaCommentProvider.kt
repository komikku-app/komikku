package eu.kanade.tachiyomi.source.comment.copymanga

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.comment.CommentPage
import eu.kanade.tachiyomi.source.comment.CommentProvider
import eu.kanade.tachiyomi.source.comment.CommentRequest
import eu.kanade.tachiyomi.source.comment.SourceComment
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.COPY_MANGA_PACKAGE
import exh.source.getMainSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * CopyManga chapter comments ("roasts").
 *
 * Roasts are per-chapter. Opening the sheet loads a small newest-chapter batch for a fast first
 * paint; **Load more** (same UI as MangaDex) either pages the buffered comments or fetches the
 * next older chapter batch when the buffer runs out.
 *
 * Uses [NetworkHelper.client] so extension encrypt/decrypt interceptors cannot break plaintext JSON.
 */
class CopyMangaCommentProvider : CommentProvider {

    private val sourceManager: SourceManager by injectLazy()
    private val networkHelper: NetworkHelper by injectLazy()
    private val context: Application by injectLazy()

    /**
     * @param chapters Newest-first chapters with parseable roast UUIDs.
     * @param nextChapterIndex Next chapter index to fetch on expand.
     * @param comments Deduped, newest-first buffer built so far.
     */
    private data class Session(
        val chapters: List<SChapter>,
        val nextChapterIndex: Int,
        val comments: List<SourceComment>,
        val expiresAtMs: Long,
    ) {
        val hasMoreChapters: Boolean get() = nextChapterIndex < chapters.size
    }

    private val sessions = ConcurrentHashMap<Long, Session>()

    override fun supports(sourceId: Long, packageName: String?): Boolean = packageName == COPY_MANGA_PACKAGE

    override suspend fun commentCount(request: CommentRequest): Int? = null

    override suspend fun loadPage(request: CommentRequest, page: Int): CommentPage {
        val safePage = page.coerceAtLeast(1)
        val source = sourceManager.get(request.sourceId)?.getMainSource<HttpSource>()
        val client = networkHelper.client
        val headers = buildHeaders(source?.headers)
        val hosts = CopyMangaRoastParser.apiHosts(source?.baseUrl)

        val session = if (safePage == 1) {
            startSession(request, client, headers, hosts)
        } else {
            expandSession(request, client, headers, hosts, neededCount = safePage * PAGE_SIZE)
        }

        val from = (safePage - 1) * PAGE_SIZE
        // If still short after expand (empty older chapters), keep expanding until full or exhausted
        var current = session
        while (current.comments.size < from + PAGE_SIZE && current.hasMoreChapters) {
            current = expandSession(request, client, headers, hosts, neededCount = from + PAGE_SIZE)
        }

        val slice = current.comments.drop(from).take(PAGE_SIZE)
        val hasNext = from + slice.size < current.comments.size || current.hasMoreChapters
        return CommentPage(
            page = safePage,
            comments = slice,
            hasNextPage = hasNext,
            // Growing count while more chapters remain; final once exhausted
            totalCount = current.comments.size,
            subtitle = when {
                current.chapters.size > 1 -> context.stringResource(KMR.strings.comments_recent_chapters)
                else -> current.chapters.singleOrNull()?.name
                    ?.takeIf { it.isNotBlank() }
                    ?.let { context.stringResource(KMR.strings.comments_for_chapter, it) }
            },
        )
    }

    private suspend fun startSession(
        request: CommentRequest,
        client: OkHttpClient,
        headers: Headers,
        hosts: List<String>,
    ): Session {
        val chapters = resolveChapters(request, client, headers, hosts)
        if (chapters.isEmpty()) {
            throw IllegalStateException(context.stringResource(KMR.strings.error_comments_no_chapter))
        }
        val now = System.currentTimeMillis()
        val end = minOf(CHAPTER_BATCH, chapters.size)
        val comments = fetchAndMerge(emptyList(), chapters.subList(0, end), client, headers, hosts)
        return Session(
            chapters = chapters,
            nextChapterIndex = end,
            comments = comments,
            expiresAtMs = now + CACHE_TTL_MS,
        ).also { sessions[request.mangaId] = it }
    }

    private suspend fun expandSession(
        request: CommentRequest,
        client: OkHttpClient,
        headers: Headers,
        hosts: List<String>,
        neededCount: Int,
    ): Session {
        val now = System.currentTimeMillis()
        var session = sessions[request.mangaId]?.takeIf { it.expiresAtMs > now }
            ?: return startSession(request, client, headers, hosts)

        while (session.comments.size < neededCount && session.hasMoreChapters) {
            val from = session.nextChapterIndex
            val to = minOf(from + CHAPTER_BATCH, session.chapters.size)
            val batch = session.chapters.subList(from, to)
            val merged = fetchAndMerge(session.comments, batch, client, headers, hosts)
            session = session.copy(
                nextChapterIndex = to,
                comments = merged,
                expiresAtMs = now + CACHE_TTL_MS,
            )
            sessions[request.mangaId] = session
        }
        return session
    }

    private suspend fun fetchAndMerge(
        existing: List<SourceComment>,
        chapters: List<SChapter>,
        client: OkHttpClient,
        headers: Headers,
        hosts: List<String>,
    ): List<SourceComment> {
        val merged = LinkedHashMap<String, SourceComment>()
        existing.forEach { merged[it.id] = it }
        chapters.forEach { chapter ->
            val uuid = CopyMangaRoastParser.extractChapterUuid(chapter.url) ?: return@forEach
            fetchChapterRoasts(uuid, client, headers, hosts).forEach { comment ->
                merged.putIfAbsent(comment.id, comment)
            }
        }
        return merged.values.sortedWith(
            compareByDescending<SourceComment> { it.dateEpochMillis ?: 0L }
                .thenByDescending { it.id },
        )
    }

    private suspend fun resolveChapters(
        request: CommentRequest,
        client: OkHttpClient,
        headers: Headers,
        hosts: List<String>,
    ): List<SChapter> {
        val fromRequest = (request.recentChapters.ifEmpty { listOfNotNull(request.latestChapter) })
            .filter { CopyMangaRoastParser.extractChapterUuid(it.url) != null }
            .take(MAX_CHAPTERS)
        if (fromRequest.isNotEmpty()) return fromRequest

        val pathWord = CopyMangaRoastParser.extractPathWord(request.manga.url) ?: return emptyList()
        val body = fetchWithHostFallback(hosts, client, headers) { host ->
            "$host/api/v3/comic2/$pathWord?platform=1"
        }
        val last = CopyMangaRoastParser.parseLastChapter(body) ?: return emptyList()
        return listOf(
            SChapter.create().apply {
                url = last.uuid
                name = last.name.orEmpty()
            },
        )
    }

    /** First roast page for one chapter (server pins limit ≈ 10). */
    private suspend fun fetchChapterRoasts(
        chapterUuid: String,
        client: OkHttpClient,
        headers: Headers,
        hosts: List<String>,
    ): List<SourceComment> {
        val body = fetchWithHostFallback(hosts, client, headers) { host ->
            "$host/api/v3/roasts?chapter_id=$chapterUuid&limit=$PAGE_SIZE&offset=0&_update=true"
        }
        return CopyMangaRoastParser.parse(body).comments
    }

    private suspend fun fetchWithHostFallback(
        hosts: List<String>,
        client: OkHttpClient,
        headers: Headers,
        urlFor: (String) -> String,
    ): String {
        var lastError: Exception? = null
        hosts.forEach { host ->
            try {
                return client.newCall(GET(urlFor(host), headers)).awaitSuccess().body.string()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code == 429) {
                    throw IllegalStateException(context.stringResource(KMR.strings.error_comments_rate_limited), e)
                }
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("CopyManga API unreachable")
    }

    private fun buildHeaders(sourceHeaders: Headers?): Headers {
        return Headers.Builder()
            .set("User-Agent", sourceHeaders?.get("User-Agent") ?: DEFAULT_USER_AGENT)
            .set("platform", "1")
            .set("region", "1")
            .set("webp", "1")
            .set("version", sourceHeaders?.get("version") ?: DEFAULT_API_VERSION)
            .set("Accept", "application/json")
            .build()
    }

    companion object {
        private const val PAGE_SIZE = 10
        /** Chapters fetched on first open / each expand. */
        private const val CHAPTER_BATCH = 5
        /** Hard cap of newest chapters available to Load more. */
        const val MAX_CHAPTERS = 60
        private const val CACHE_TTL_MS = 5L * 60L * 1000L
        private const val DEFAULT_API_VERSION = "2025.11.08"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
    }
}
