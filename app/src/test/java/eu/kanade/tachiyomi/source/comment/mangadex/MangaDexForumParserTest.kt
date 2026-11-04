package eu.kanade.tachiyomi.source.comment.mangadex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaDexForumParserTest {

    private fun post(
        id: Long,
        author: String,
        body: String,
        epochSeconds: Long? = 1_714_739_696L,
        datetime: String = "2024-05-03T12:34:56+0000",
    ): String {
        val timeAttr = epochSeconds?.let { " data-time=\"$it\"" }.orEmpty()
        return """
        <article class="message message--post js-post" data-author="$author" data-content="post-$id" id="js-post-$id">
          <div class="message-inner">
            <div class="message-cell message-cell--main">
              <header class="message-attribution">
                <a href="/threads/t/post-$id"><time class="u-dt" datetime="$datetime"$timeAttr>May 3, 2024</time></a>
              </header>
              <div class="message-content">
                <article class="message-body js-selectToQuote"><div class="bbWrapper">$body</div></article>
              </div>
            </div>
          </div>
        </article>
        """.trimIndent()
    }

    private fun threadPage(posts: String, nav: String = ""): String = """
        <html><body>
          <div class="block-body js-replyNewMessageContainer">$posts</div>
          $nav
        </body></html>
    """.trimIndent()

    @Test
    fun `parses author body id and epoch date`() {
        val html = threadPage(post(1001, "alice", "Great chapter!<br>Loved it."))

        val page = MangaDexForumParser.parseThreadPage(html)

        assertEquals(1, page.posts.size)
        val comment = page.posts.single()
        assertEquals("1001", comment.id)
        assertEquals("alice", comment.username)
        assertEquals("Great chapter!\nLoved it.", comment.body)
        assertEquals(1_714_739_696_000L, comment.dateEpochMillis)
        assertFalse(comment.isSpoiler)
        assertEquals(1, page.totalPages)
        assertEquals(1, page.currentPage)
    }

    @Test
    fun `falls back to datetime attribute without data-time`() {
        val html = threadPage(post(7, "bob", "hi", epochSeconds = null, datetime = "2024-05-03T12:34:56+0000"))

        val comment = MangaDexForumParser.parseThreadPage(html).posts.single()

        assertEquals(1_714_739_696_000L, comment.dateEpochMillis)
    }

    @Test
    fun `skips empty posts and strips quote chrome`() {
        val quoted = """
            <blockquote class="bbCodeBlock bbCodeBlock--quote">
              <div class="bbCodeBlock-title"><a href="#">alice said:</a></div>
              <div class="bbCodeBlock-content">
                <div class="bbCodeBlock-expandContent">Great chapter!</div>
                <div class="bbCodeBlock-expandLink js-expandLink"><a role="button">Click to expand...</a></div>
              </div>
            </blockquote>
            Agreed.
        """.trimIndent()
        val html = threadPage(post(1, "MangaDex", "   ") + post(2, "carol", quoted))

        val posts = MangaDexForumParser.parseThreadPage(html).posts

        assertEquals(listOf("2"), posts.map { it.id })
        assertEquals("Great chapter!\n\nAgreed.", posts.single().body)
    }

    @Test
    fun `flags spoiler blocks and drops the spoiler button text`() {
        val spoiler = """
            <div class="bbCodeSpoiler">
              <button type="button" class="bbCodeSpoiler-button button"><span>Spoiler</span></button>
              <div class="bbCodeSpoiler-content"><div class="bbCodeBlock bbCodeBlock--spoiler">
                <div class="bbCodeBlock-content">He dies.</div></div></div>
            </div>
        """.trimIndent()

        val comment = MangaDexForumParser.parseThreadPage(threadPage(post(3, "dave", spoiler))).posts.single()

        assertTrue(comment.isSpoiler)
        assertEquals("He dies.", comment.body)
    }

    @Test
    fun `reads total pages from page nav`() {
        val nav = """
            <ul class="pageNav-main">
              <li class="pageNav-page pageNav-page--current"><a href="/threads/t/">1</a></li>
              <li class="pageNav-page"><a href="/threads/t/page-2">2</a></li>
              <li class="pageNav-page pageNav-page--later"><a href="/threads/t/page-5">5</a></li>
            </ul>
        """.trimIndent()

        val page = MangaDexForumParser.parseThreadPage(threadPage(post(1, "a", "x"), nav))

        assertEquals(5, page.totalPages)
        assertEquals(1, page.currentPage)
    }

    @Test
    fun `reads total pages from simple nav fallback`() {
        val nav = """
            <div class="pageNavSimple">
              <span class="pageNavSimple-el pageNavSimple-el--current">Page 2 of 3</span>
            </div>
        """.trimIndent()

        val page = MangaDexForumParser.parseThreadPage(threadPage(post(1, "a", "x"), nav))

        assertEquals(3, page.totalPages)
    }

    @Test
    fun `detects MangaDex edge challenge pages`() {
        assertTrue(
            MangaDexForumParser.looksLikeChallenge(
                "Redirecting to bot challenge page... (if this isn't a browser, don't send a browser UA)",
            ),
        )
        assertTrue(
            MangaDexForumParser.looksLikeChallenge(
                """<html><link rel="stylesheet" href="challenge-assets/challenge.css"><body>wait</body></html>""",
            ),
        )
        assertFalse(MangaDexForumParser.looksLikeChallenge(threadPage(post(1, "a", "hello"))))
    }
}
