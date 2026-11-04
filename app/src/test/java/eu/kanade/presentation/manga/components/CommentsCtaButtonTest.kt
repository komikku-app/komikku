package eu.kanade.presentation.manga.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommentsCtaButtonTest {

    @Test
    fun `formatCommentBadge keeps small counts as-is`() {
        assertEquals("0", formatCommentBadge(0))
        assertEquals("18", formatCommentBadge(18))
        assertEquals("999", formatCommentBadge(999))
    }

    @Test
    fun `formatCommentBadge abbreviates thousands`() {
        assertEquals("1K", formatCommentBadge(1000))
        assertEquals("1.5K", formatCommentBadge(1500))
        assertEquals("12K", formatCommentBadge(12_000))
        assertEquals("1M", formatCommentBadge(1_000_000))
    }
}
