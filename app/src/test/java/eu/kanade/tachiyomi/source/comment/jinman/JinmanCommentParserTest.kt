package eu.kanade.tachiyomi.source.comment.jinman

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JinmanCommentParserTest {

    private val fragment = """
        <div id="comment_list">
          <div class="timeline" data-id="5001">
            <div class="timeline-left"><a href="/user/alice"><img class="timeline-avatar" src="/a.png"></a></div>
            <div class="timeline-content">
              <div class="timeline-header">
                <div class="timeline-username"><a href="/user/alice">alice</a></div>
                <span class="timeline-date">2024-05-01</span>
                <div class="timeline-like">12</div>
              </div>
              <div class="timeline-content-comment">好看！<br>推荐</div>
              <div class="timeline-reply-btn"><a>回复</a></div>
              <div class="timeline-reply">
                <div class="timeline" data-id="5002">
                  <div class="timeline-content">
                    <div class="timeline-header">
                      <div class="timeline-username"><a href="/user/bob">bob</a></div>
                      <span class="timeline-date">2024-05-02 08:30</span>
                    </div>
                    <div class="timeline-content-comment">同意</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="timeline" data-id="5003">
            <div class="timeline-content">
              <div class="timeline-header">
                <div class="timeline-username"><a href="/user/carol">carol</a></div>
                <span class="timeline-date">2024-05-03</span>
              </div>
              <div class="timeline-content-comment"><span class="text-spoiler">结局剧透</span></div>
            </div>
          </div>
          <div class="timeline" data-id="5004">
            <div class="timeline-content"><div class="timeline-content-comment">   </div></div>
          </div>
        </div>
    """.trimIndent()

    @Test
    fun `parses root comments with nested replies and skips empty bodies`() {
        val comments = JinmanCommentParser.parse(fragment)

        assertEquals(listOf("5001", "5003"), comments.map { it.id })

        val first = comments[0]
        assertEquals("alice", first.username)
        assertEquals("好看！\n推荐", first.body)
        assertEquals(1_714_492_800_000L, first.dateEpochMillis) // 2024-05-01 00:00 +08:00
        assertFalse(first.isSpoiler)
        assertEquals(1, first.replies.size)
        assertEquals("5002", first.replies[0].id)
        assertEquals("bob", first.replies[0].username)
        assertEquals("同意", first.replies[0].body)
        assertEquals(1_714_609_800_000L, first.replies[0].dateEpochMillis) // 2024-05-02 08:30 +08:00
        assertTrue(first.replies[0].replies.isEmpty())

        val spoiler = comments[1]
        assertTrue(spoiler.isSpoiler)
        assertEquals("结局剧透", spoiler.body)
        assertEquals("carol", spoiler.username)
    }

    @Test
    fun `fallback ids are scoped by page`() {
        val html = """<div class="timeline"><div class="timeline-content-comment">x</div></div>"""

        assertEquals("p1-root-0", JinmanCommentParser.parse(html, page = 1).single().id)
        assertEquals("p2-root-0", JinmanCommentParser.parse(html, page = 2).single().id)
    }

    @Test
    fun `strips reply CTA and chapter index chrome from body`() {
        val html = """
            <div class="timeline" data-id="9">
              <div class="timeline-username">bosszs</div>
              <div class="timeline-content-comment">
                这个谁知道有没有小说啊？
                <div class="timeline-reply-btn"><a>回覆</a></div>
                <span class="timeline-chapter">第1話 1</span>
              </div>
            </div>
        """.trimIndent()

        val comment = JinmanCommentParser.parse(html).single()

        assertEquals("这个谁知道有没有小说啊？", comment.body)
        assertFalse(comment.body.contains("回覆"))
        assertFalse(comment.body.contains("第1話"))
    }

    @Test
    fun `extractHtml unwraps JSON pagination payloads`() {
        val html = """<div class="timeline"><div class="timeline-content-comment">hi</div></div>"""
        val escaped = html.replace("\"", "\\\"")

        assertEquals(html, JinmanCommentParser.extractHtml("""{"code": 200, "message": "$escaped"}"""))
        assertEquals(html, JinmanCommentParser.extractHtml("""{"code": "$escaped", "message": ""}"""))
        assertEquals("", JinmanCommentParser.extractHtml("""{"code": 200, "message": ""}"""))
        assertEquals(html, JinmanCommentParser.extractHtml("  $html  "))
        assertTrue(JinmanCommentParser.parse("").isEmpty())
    }

    @Test
    fun `extracts album id from url variants`() {
        assertEquals("350234", JinmanCommentParser.extractAlbumId("/album/350234/"))
        assertEquals("350234", JinmanCommentParser.extractAlbumId("/album/350234/some-title"))
        assertEquals("350234", JinmanCommentParser.extractAlbumId("https://18comic.vip/album/350234/x?y=1"))
        assertNull(JinmanCommentParser.extractAlbumId("/photo/abc"))
    }
}
