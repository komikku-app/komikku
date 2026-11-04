package eu.kanade.tachiyomi.source.comment.copymanga

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.comment.CommentPage
import eu.kanade.tachiyomi.source.comment.CommentProvider
import eu.kanade.tachiyomi.source.comment.CommentRequest
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.COPY_MANGA_PACKAGE
import exh.source.getMainSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.injectLazy
import kotlin.coroutines.cancellation.CancellationException

/**
 * CopyManga chapter comments ("roasts"). The manga page is scoped to the newest chapter
 * (from stored chapters, else `comic2` detail `last_chapter`); the sheet subtitle says which.
 */
class CopyMangaCommentProvider : CommentProvider {

    private val sourceManager: SourceManager by injectLazy()
    private val networkHelper: NetworkHelper by injectLazy()
    private val context: Application by injectLazy()

    override fun supports(sourceId: Long, packageName: String?): Boolean = packageName == COPY_MANGA_PACKAGE

    /** Chapter-scoped; the count is only known once the first page loads. */
    override suspend fun commentCount(request: CommentRequest): Int? = null

    override suspend fun loadPage(request: CommentRequest, page: Int): CommentPage {
        val safePage = page.coerceAtLeast(1)
        val source = sourceManager.get(request.sourceId)?.getMainSource<HttpSource>()
        val client = source?.client ?: networkHelper.client
        val headers = buildHeaders(source?.headers)
        val hosts = CopyMangaRoastParser.apiHosts(source?.baseUrl)

        val chapter = resolveChapter(request, client, headers, hosts)
            ?: throw IllegalStateException(context.stringResource(KMR.strings.error_comments_no_chapter))

        val offset = (safePage - 1) * PAGE_SIZE
        val body = fetchWithHostFallback(hosts, client, headers) { host ->
            "$host/api/v3/roasts?chapter_id=${chapter.uuid}&limit=$PAGE_SIZE&offset=$offset&_update=true"
        }
        val roasts = CopyMangaRoastParser.parse(body)
        val loaded = (roasts.offset ?: offset) + roasts.comments.size
        return CommentPage(
            page = safePage,
            comments = roasts.comments,
            hasNextPage = roasts.total?.let { loaded < it } ?: (roasts.comments.size >= PAGE_SIZE),
            totalCount = roasts.total,
            subtitle = chapter.name?.let { context.stringResource(KMR.strings.comments_for_chapter, it) },
        )
    }

    private suspend fun resolveChapter(
        request: CommentRequest,
        client: OkHttpClient,
        headers: Headers,
        hosts: List<String>,
    ): CopyMangaChapterRef? {
        request.latestChapter?.let { chapter ->
            CopyMangaRoastParser.extractChapterUuid(chapter.url)?.let { uuid ->
                return CopyMangaChapterRef(uuid = uuid, name = chapter.name.takeIf { it.isNotBlank() })
            }
        }
        val pathWord = CopyMangaRoastParser.extractPathWord(request.manga.url) ?: return null
        val body = fetchWithHostFallback(hosts, client, headers) { host ->
            "$host/api/v3/comic2/$pathWord?platform=1"
        }
        return CopyMangaRoastParser.parseLastChapter(body)
    }

    /** Tries each API host in order; rate limits abort immediately with a readable message. */
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
        val builder = sourceHeaders?.newBuilder() ?: Headers.Builder().add("User-Agent", DEFAULT_USER_AGENT)
        builder.set("platform", "1")
            .set("region", "1")
            .set("webp", "1")
            .set("Accept", "application/json")
        if (sourceHeaders?.get("version") == null) builder.set("version", DEFAULT_API_VERSION)
        return builder.build()
    }

    companion object {
        /** Server pins `limit` to 10 regardless of the query parameter. */
        private const val PAGE_SIZE = 10
        private const val DEFAULT_API_VERSION = "2025.11.08"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
    }
}
