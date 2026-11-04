package eu.kanade.tachiyomi.source.comment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId

class CommentHtmlTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val now = 1_730_000_000_000L // fixed

    @Test
    fun `parses relative chinese times`() {
        assertEquals(now, CommentHtml.parseRelativeDate("剛剛", now))
        assertEquals(now - 5 * 60_000L, CommentHtml.parseRelativeDate("5分鐘前", now))
        assertEquals(now - 3 * 3_600_000L, CommentHtml.parseRelativeDate("3小时前", now))
        assertEquals(now - 2 * 86_400_000L, CommentHtml.parseRelativeDate("2天前", now))
        assertEquals(now - 1 * 604_800_000L, CommentHtml.parseRelativeDate("1週前", now))
    }

    @Test
    fun `parseFlexibleDate prefers unix then relative`() {
        assertEquals(1_714_492_800_000L, CommentHtml.parseFlexibleDate("1714492800", shanghai, now))
        assertEquals(now - 86_400_000L, CommentHtml.parseFlexibleDate("1天前", shanghai, now))
        assertTrue(
            CommentHtml.parseFlexibleDate("2024-5-1", shanghai, now) != null,
        )
        assertNull(CommentHtml.parseFlexibleDate("not a date", shanghai, now))
    }
}
