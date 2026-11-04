package eu.kanade.tachiyomi.source.comment.mangadex

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.comment.CommentPage
import eu.kanade.tachiyomi.source.comment.CommentProvider
import eu.kanade.tachiyomi.source.comment.CommentRequest
import exh.md.dto.StatisticsDto
import exh.md.utils.MdApi
import exh.md.utils.MdUtil
import exh.source.MANGADEX_IDS
import okhttp3.Headers
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

/**
 * MangaDex comments: thread id from the statistics API, posts scraped from the XenForo forum.
 * Logical page 1 is the newest forum page; posts are returned newest-first.
 *
 * Forum fetches use a non-browser User-Agent: a browser UA is redirected to MangaDex's edge
 * challenge page, which parses as zero posts.
 */
class MangaDexCommentProvider(
    private val forumBaseUrl: String = FORUM_BASE_URL,
    private val cacheTtlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : CommentProvider {

    private val networkHelper: NetworkHelper by injectLazy()
    private val context: Application by injectLazy()

    private data class ThreadInfo(val threadId: Long?, val repliesCount: Int?, val expiresAtMs: Long)

    private val threadCache = ConcurrentHashMap<String, ThreadInfo>()
    private val totalPagesCache = ConcurrentHashMap<Long, Int>()

    override fun supports(sourceId: Long, packageName: String?): Boolean = sourceId in MANGADEX_IDS

    override suspend fun commentCount(request: CommentRequest): Int? =
        request.knownCount ?: resolveThread(request)?.repliesCount

    override suspend fun loadPage(request: CommentRequest, page: Int): CommentPage {
        val safePage = page.coerceAtLeast(1)
        val thread = resolveThread(request)
        val threadId = thread?.threadId
            ?: return CommentPage(
                page = safePage,
                comments = emptyList(),
                hasNextPage = false,
                totalCount = thread?.repliesCount ?: 0,
            )

        val forumPage: ForumThreadPage
        val totalPages: Int
        if (safePage == 1) {
            val first = fetchForumPage(threadId, 1)
            totalPages = first.totalPages
            forumPage = if (totalPages <= 1) first else fetchForumPage(threadId, totalPages)
            totalPagesCache[threadId] = totalPages
        } else {
            totalPages = totalPagesCache[threadId] ?: fetchForumPage(threadId, 1).totalPages.also {
                totalPagesCache[threadId] = it
            }
            val target = totalPages - (safePage - 1)
            if (target < 1) {
                return CommentPage(safePage, emptyList(), hasNextPage = false, totalCount = thread.repliesCount)
            }
            forumPage = fetchForumPage(threadId, target)
        }

        // Stats said there are replies, but HTML had no posts → challenge / blocked page, not empty thread
        val expected = thread.repliesCount ?: request.knownCount
        if (safePage == 1 && forumPage.posts.isEmpty() && expected != null && expected > 0) {
            throw IllegalStateException(context.stringResource(KMR.strings.error_comments_blocked))
        }

        val forumIndex = totalPages - (safePage - 1)
        return CommentPage(
            page = safePage,
            comments = forumPage.posts.asReversed(),
            hasNextPage = forumIndex > 1,
            totalCount = thread.repliesCount,
        )
    }

    private suspend fun resolveThread(request: CommentRequest): ThreadInfo? {
        request.threadId?.let { return ThreadInfo(it, request.knownCount, Long.MAX_VALUE) }
        val uuid = MdUtil.getMangaId(request.manga.url).takeIf { it.isNotBlank() } ?: return null
        val now = nowMs()
        threadCache[uuid]?.takeIf { it.expiresAtMs > now }?.let { return it }

        val stats = with(MdUtil.jsonParser) {
            networkHelper.client
                .newCall(GET("${MdApi.statistics}/$uuid", Headers.headersOf("Accept", "application/json")))
                .awaitSuccess()
                .parseAs<StatisticsDto>()
        }.statistics[uuid]
        val info = ThreadInfo(
            threadId = stats?.comments?.threadId,
            repliesCount = stats?.comments?.repliesCount,
            expiresAtMs = now + cacheTtlMs,
        )
        threadCache[uuid] = info
        return info
    }

    private suspend fun fetchForumPage(threadId: Long, page: Int): ForumThreadPage {
        val url = if (page <= 1) {
            "$forumBaseUrl/threads/$threadId/"
        } else {
            "$forumBaseUrl/threads/$threadId/page-$page"
        }
        // Explicit non-browser UA: browser UAs are sent to mangadex.org/edge/challenge
        val headers = Headers.Builder()
            .set("Accept", "text/html,application/xhtml+xml")
            .set("User-Agent", FORUM_USER_AGENT)
            .build()
        val html = networkHelper.client
            .newCall(GET(url, headers))
            .awaitSuccess()
            .body
            .string()
        if (MangaDexForumParser.looksLikeChallenge(html)) {
            throw IllegalStateException(context.stringResource(KMR.strings.error_comments_blocked))
        }
        return MangaDexForumParser.parseThreadPage(html)
    }

    companion object {
        const val FORUM_BASE_URL = "https://forums.mangadex.org"
        /** Non-browser UA avoids MangaDex edge challenge that strips forum posts. */
        const val FORUM_USER_AGENT = "okhttp/4.12.0"
        private const val DEFAULT_TTL_MS = 5L * 60L * 1000L
    }
}
