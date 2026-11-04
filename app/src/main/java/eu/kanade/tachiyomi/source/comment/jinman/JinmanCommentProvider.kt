package eu.kanade.tachiyomi.source.comment.jinman

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.comment.CommentPage
import eu.kanade.tachiyomi.source.comment.CommentProvider
import eu.kanade.tachiyomi.source.comment.CommentRequest
import eu.kanade.tachiyomi.source.comment.SourceComment
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.JINMAN_PACKAGE
import exh.source.getMainSource
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.injectLazy
import kotlin.coroutines.cancellation.CancellationException

/**
 * Jinman Tiantang album comments scraped from the active web mirror (extension base URL).
 * Page 1 falls back to the album page when the AJAX pagination endpoint yields nothing.
 * Failures never reach the manga page; the sheet shows a soft error with retry.
 */
class JinmanCommentProvider : CommentProvider {

    private val sourceManager: SourceManager by injectLazy()
    private val context: Application by injectLazy()

    override fun supports(sourceId: Long, packageName: String?): Boolean = packageName == JINMAN_PACKAGE

    /** Unknown until the first page is loaded. */
    override suspend fun commentCount(request: CommentRequest): Int? = null

    override suspend fun loadPage(request: CommentRequest, page: Int): CommentPage {
        val safePage = page.coerceAtLeast(1)
        val albumId = JinmanCommentParser.extractAlbumId(request.manga.url)
            ?: throw IllegalStateException(context.stringResource(KMR.strings.error_comments_unsupported_url))
        // The active mirror only lives in the extension's preferences, so its HttpSource is required
        val source = sourceManager.get(request.sourceId)?.getMainSource<HttpSource>()
            ?: throw IllegalStateException(context.stringResource(KMR.strings.error_comments_mirror_unavailable))
        val baseUrl = source.baseUrl.trimEnd('/')
        val client = source.client
        val headers = source.headers.newBuilder()
            .set("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Referer", "$baseUrl/album/$albumId/")
            .build()

        val comments = try {
            val paginated = fetchPaginated(client, headers, baseUrl, albumId, safePage)
            if (paginated.isEmpty() && safePage == 1) {
                fetchFromAlbumPage(client, headers, baseUrl, albumId)
            } else {
                paginated
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            throw IllegalStateException(
                context.stringResource(KMR.strings.error_comments_mirror_unavailable) + detail,
                e,
            )
        }

        return CommentPage(
            page = safePage,
            comments = comments,
            hasNextPage = comments.isNotEmpty(),
            totalCount = null,
        )
    }

    private suspend fun fetchPaginated(
        client: OkHttpClient,
        headers: Headers,
        baseUrl: String,
        albumId: String,
        page: Int,
    ): List<SourceComment> {
        val form = FormBody.Builder()
            .add("video_id", albumId)
            .add("page", page.toString())
            .add("series", "")
            .add("with_ad_wcm", "0")
            .build()
        val body = client.newCall(POST("$baseUrl/ajax/album_pagination", headers, form)).awaitSuccess().body.string()
        return JinmanCommentParser.parse(JinmanCommentParser.extractHtml(body), page)
    }

    private suspend fun fetchFromAlbumPage(
        client: OkHttpClient,
        headers: Headers,
        baseUrl: String,
        albumId: String,
    ): List<SourceComment> {
        val htmlHeaders = headers.newBuilder()
            .set("Accept", "text/html,application/xhtml+xml")
            .removeAll("X-Requested-With")
            .build()
        val html = client.newCall(GET("$baseUrl/album/$albumId/", htmlHeaders)).awaitSuccess().body.string()
        return JinmanCommentParser.parse(html)
    }
}
