package eu.kanade.tachiyomi.source.comment.copymanga

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CopyMangaRoastParserTest {

    private val roastsJson = """
        {
          "code": 200,
          "message": "Success.",
          "results": {
            "total": 42,
            "list": [
              {
                "id": 78423,
                "create_at": "2026-10-31 14:11:11",
                "user_id": "cc5e913c-eba1-11eb-b166-51d3def67315",
                "user_name": "unia",
                "user_avatar": "https://s3.example/user/cover/copymanga.png",
                "comment": "这一话错字好多"
              },
              {
                "id": "78407",
                "create_at": "2026-10-30 19:20:13",
                "user_name": "",
                "comment": "评论区还有人类吗"
              },
              { "id": 1, "create_at": null, "user_name": "ghost", "comment": "   " }
            ],
            "limit": 10,
            "offset": 0
          }
        }
    """.trimIndent()

    @Test
    fun `parses roasts keeping API newest-first order`() {
        val page = CopyMangaRoastParser.parse(roastsJson)

        assertEquals(42, page.total)
        assertEquals(10, page.limit)
        assertEquals(0, page.offset)
        assertEquals(listOf("78423", "78407"), page.comments.map { it.id })
        assertEquals("unia", page.comments[0].username)
        assertEquals("这一话错字好多", page.comments[0].body)
        assertEquals("anonymous", page.comments[1].username)
    }

    @Test
    fun `create_at is parsed as Asia Shanghai local time`() {
        // 2026-10-31 14:11:11 +08:00 == 2026-10-31T06:11:11Z
        assertEquals(1_793_427_071_000L, CopyMangaRoastParser.parseDate("2026-10-31 14:11:11"))
        assertNull(CopyMangaRoastParser.parseDate(null))
        assertNull(CopyMangaRoastParser.parseDate("yesterday"))
    }

    @Test
    fun `non-200 code surfaces API message`() {
        val error = assertThrows(IllegalStateException::class.java) {
            CopyMangaRoastParser.parse("""{"code": 429, "message": "请求过于频繁", "results": null}""")
        }
        assertEquals("请求过于频繁", error.message)
    }

    @Test
    fun `missing results is an empty page`() {
        val page = CopyMangaRoastParser.parse("""{"code": 200, "message": "ok"}""")
        assertEquals(0, page.total)
        assertEquals(emptyList<String>(), page.comments.map { it.id })
    }

    @Test
    fun `extracts chapter uuid from stored chapter urls`() {
        assertEquals(
            "fd1b3104-c2d4-11f1-b213-fa163e02432f",
            CopyMangaRoastParser.extractChapterUuid(
                "/comic/yiquanchaoren/chapter2/FD1B3104-C2D4-11F1-B213-FA163E02432F",
            ),
        )
        assertEquals(
            "33ba8395-654a-11f0-b194-fa163e02432f",
            CopyMangaRoastParser.extractChapterUuid("/comic/x/chapter/33ba8395-654a-11f0-b194-fa163e02432f?p=1"),
        )
        assertNull(CopyMangaRoastParser.extractChapterUuid("/comic/x/chapter/12345"))
        assertNull(CopyMangaRoastParser.extractChapterUuid(null))
    }

    @Test
    fun `extracts path word from manga url`() {
        assertEquals("yiquanchaoren", CopyMangaRoastParser.extractPathWord("/comic/yiquanchaoren"))
        assertEquals(
            "yiquanchaoren",
            CopyMangaRoastParser.extractPathWord("https://www.mangacopy.com/comic/yiquanchaoren/?x=1"),
        )
        assertNull(CopyMangaRoastParser.extractPathWord("/"))
    }

    @Test
    fun `api hosts derive from extension base url then fall back`() {
        assertEquals(
            listOf("https://api.copy20.com", "https://api.mangacopy.com", "https://api.2025copy.com"),
            CopyMangaRoastParser.apiHosts("https://www.copy20.com/"),
        )
        assertEquals(
            listOf("https://api.mangacopy.com", "https://api.2025copy.com"),
            CopyMangaRoastParser.apiHosts("https://www.mangacopy.com"),
        )
        assertEquals(
            listOf("https://api.mangacopy.com", "https://api.2025copy.com"),
            CopyMangaRoastParser.apiHosts(null),
        )
    }

    @Test
    fun `parses last chapter from comic2 detail`() {
        val detail = """
            {"code":200,"results":{"comic":{"uuid":"u","path_word":"yiquanchaoren","name":"一拳超人",
             "last_chapter":{"uuid":"fd1b3104-c2d4-11f1-b213-fa163e02432f","name":"第286话"}}}}
        """.trimIndent()

        val ref = CopyMangaRoastParser.parseLastChapter(detail)

        assertEquals("fd1b3104-c2d4-11f1-b213-fa163e02432f", ref?.uuid)
        assertEquals("第286话", ref?.name)
        assertNull(CopyMangaRoastParser.parseLastChapter("""{"code":200,"results":{"comic":{}}}"""))
    }
}
