package eu.kanade.tachiyomi.source.comment

import eu.kanade.tachiyomi.source.comment.copymanga.CopyMangaCommentProvider
import eu.kanade.tachiyomi.source.comment.jinman.JinmanCommentProvider
import eu.kanade.tachiyomi.source.comment.mangadex.MangaDexCommentProvider
import eu.kanade.tachiyomi.source.model.SManga
import exh.source.COPY_MANGA_PACKAGE
import exh.source.JINMAN_PACKAGE
import exh.source.MANGADEX_IDS
import exh.source.MANGAFIRE_PACKAGE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommentProviderRegistryTest {

    private class FakeProvider(
        private val packages: Set<String> = emptySet(),
        private val sourceIds: Set<Long> = emptySet(),
        private val count: Int? = null,
    ) : CommentProvider {
        override fun supports(sourceId: Long, packageName: String?): Boolean =
            (packageName != null && packageName in packages) || sourceId in sourceIds

        override suspend fun commentCount(request: CommentRequest): Int? = count

        override suspend fun loadPage(request: CommentRequest, page: Int): CommentPage =
            CommentPage(page = page, comments = emptyList(), hasNextPage = false)
    }

    private fun productionRegistry() = CommentProviderRegistry(
        listOf(MangaDexCommentProvider(), CopyMangaCommentProvider(), JinmanCommentProvider()),
    )

    @Test
    fun `empty registry has no providers`() {
        val registry = CommentProviderRegistry()
        assertFalse(registry.exists(sourceId = 1L, packageName = COPY_MANGA_PACKAGE))
        assertNull(registry.get(sourceId = 1L, packageName = COPY_MANGA_PACKAGE))
    }

    @Test
    fun `routes CopyManga by package name`() {
        val copyManga = FakeProvider(packages = setOf(COPY_MANGA_PACKAGE), count = 3)
        val registry = CommentProviderRegistry(listOf(copyManga))

        assertTrue(registry.exists(sourceId = 99L, packageName = COPY_MANGA_PACKAGE))
        assertSame(copyManga, registry.get(sourceId = 99L, packageName = COPY_MANGA_PACKAGE))
        assertFalse(registry.exists(sourceId = 99L, packageName = "eu.kanade.tachiyomi.extension.other"))
        assertNull(registry.get(sourceId = 99L, packageName = null))
    }

    @Test
    fun `routes MangaDex by source id set`() {
        val mangaDexId = MANGADEX_IDS.first()
        val mangaDex = FakeProvider(sourceIds = MANGADEX_IDS, count = 18)
        val registry = CommentProviderRegistry(listOf(mangaDex))

        assertTrue(registry.exists(sourceId = mangaDexId, packageName = null))
        assertSame(mangaDex, registry.get(sourceId = mangaDexId, packageName = "ignored.pkg"))
        assertFalse(registry.exists(sourceId = 1L, packageName = null))
    }

    @Test
    fun `first matching provider wins`() {
        val first = FakeProvider(packages = setOf(COPY_MANGA_PACKAGE), count = 1)
        val second = FakeProvider(packages = setOf(COPY_MANGA_PACKAGE), count = 2)
        val registry = CommentProviderRegistry(listOf(first, second))

        assertSame(first, registry.get(sourceId = 1L, packageName = COPY_MANGA_PACKAGE))
    }

    @Test
    fun `production registry routes MD CopyManga Jinman but not MangaFire`() {
        val registry = productionRegistry()

        assertInstanceOf(
            MangaDexCommentProvider::class.java,
            registry.get(sourceId = MANGADEX_IDS.first(), packageName = null),
        )
        assertInstanceOf(
            CopyMangaCommentProvider::class.java,
            registry.get(sourceId = 1L, packageName = COPY_MANGA_PACKAGE),
        )
        assertInstanceOf(
            JinmanCommentProvider::class.java,
            registry.get(sourceId = 1L, packageName = JINMAN_PACKAGE),
        )
        assertFalse(registry.exists(sourceId = 1L, packageName = MANGAFIRE_PACKAGE))
        assertFalse(registry.exists(sourceId = 1L, packageName = null))
    }

    @Test
    fun `request carries hints without touching providers`() {
        val manga = SManga.create().apply { url = "/manga/10de26cf-f750-44b2-a504-7bb9a53e0419" }
        val request = CommentRequest(
            mangaId = 7L,
            sourceId = MANGADEX_IDS.first(),
            manga = manga,
            threadId = 2575607L,
            knownCount = 18,
        )
        assertEquals(2575607L, request.threadId)
        assertEquals(18, request.knownCount)
        assertNull(request.latestChapter)
        assertEquals(manga.url, request.copy(knownCount = null).manga.url)
    }

    @Test
    fun `models are immutable data copies`() {
        val reply = SourceComment(id = "r1", username = "bob", body = "reply")
        val comment = SourceComment(
            id = "c1",
            username = "alice",
            body = "hello",
            dateEpochMillis = 1_700_000_000_000L,
            isSpoiler = true,
            replies = listOf(reply),
        )
        val page = CommentPage(
            page = 1,
            comments = listOf(comment),
            hasNextPage = true,
            totalCount = 18,
            subtitle = "Comments for Ch. 1",
        )

        val updated = page.copy(comments = page.comments + comment.copy(id = "c2", replies = emptyList()))
        assertEquals(1, page.comments.size)
        assertEquals(2, updated.comments.size)
        assertEquals("alice", page.comments.first().username)
        assertTrue(page.comments.first().isSpoiler)
        assertEquals(1, page.comments.first().replies.size)
    }
}
