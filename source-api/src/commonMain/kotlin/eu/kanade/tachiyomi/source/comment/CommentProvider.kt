package eu.kanade.tachiyomi.source.comment

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

/**
 * A single comment from a source (read-only). Nested [replies] are used where the
 * source supports threaded replies (e.g. Jinman).
 */
@Serializable
data class SourceComment(
    val id: String,
    val username: String,
    val body: String,
    val dateEpochMillis: Long? = null,
    val isSpoiler: Boolean = false,
    val replies: List<SourceComment> = emptyList(),
)

/**
 * One page of comments. [page] is 1-indexed. Providers should return newest-first.
 */
@Serializable
data class CommentPage(
    val page: Int,
    val comments: List<SourceComment>,
    val hasNextPage: Boolean,
    val totalCount: Int? = null,
    val subtitle: String? = null,
)

/**
 * Everything a provider may need for one manga. Built by the UI layer from stored state so
 * providers never touch the database directly.
 */
data class CommentRequest(
    val mangaId: Long,
    val sourceId: Long,
    val manga: SManga,
    /** Newest known chapter, for chapter-scoped comment systems (e.g. CopyManga). */
    val latestChapter: SChapter? = null,
    /**
     * Recent chapters newest-first (CopyManga aggregates roasts across these; empty = latest only).
     */
    val recentChapters: List<SChapter> = emptyList(),
    /** Forum thread id already known from metadata (e.g. MangaDex statistics). */
    val threadId: Long? = null,
    /** Comment count already known from metadata; null means unknown. */
    val knownCount: Int? = null,
)

/**
 * App-side comment backend for a source. Matched by extension package and/or source id;
 * no extension APK changes.
 */
interface CommentProvider {

    /**
     * Whether this provider handles the given source.
     * Prefer package-name matching; fall back to known source ids (e.g. MangaDex).
     */
    fun supports(sourceId: Long, packageName: String?): Boolean

    /**
     * Optional badge count for the Comments CTA. Null means unknown (CTA may still show).
     */
    suspend fun commentCount(request: CommentRequest): Int?

    /**
     * Load one page of comments. Fetch only when the UI opens the sheet.
     */
    suspend fun loadPage(request: CommentRequest, page: Int): CommentPage
}
