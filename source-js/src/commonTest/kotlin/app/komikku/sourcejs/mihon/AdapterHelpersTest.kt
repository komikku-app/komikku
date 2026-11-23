package app.komikku.sourcejs.mihon

import app.komikku.sourcejs.model.JsMangaStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdapterHelpersTest {

    @Test
    fun komikkuIdsAreTheFirst63BitsOfTheNamespacedMd5() {
        // Expected values from Python: int.from_bytes(md5(b"mangayomi-js/<id>").digest()[:8], "big") & (2**63 - 1)
        assertEquals(2647791709291192459, JsSourceIds.komikkuId(652112892))
        assertEquals(6135048093382969232, JsSourceIds.komikkuId(0))
        assertEquals(4724264012564318460, JsSourceIds.komikkuId(-1))
        assertEquals(8609539398486257843, JsSourceIds.komikkuId(Long.MAX_VALUE))
        assertEquals(7826219004447427415, JsSourceIds.komikkuId(1234567890123))
        assertTrue((-5_000L..5_000L).all { JsSourceIds.komikkuId(it) >= 0 })
        assertEquals("source_2647791709291192459", JsSourceIds.preferencesName(JsSourceIds.komikkuId(652112892)))
    }

    @Test
    fun languagesUseMihonsCasing() {
        assertEquals("en", JsSourceIds.normalizeLang("en"))
        assertEquals("en", JsSourceIds.normalizeLang(" EN "))
        assertEquals("pt-BR", JsSourceIds.normalizeLang("pt-br"))
        assertEquals("pt-BR", JsSourceIds.normalizeLang("pt_BR"))
        assertEquals("zh-Hans", JsSourceIds.normalizeLang("zh-hans"))
        assertEquals("es-419", JsSourceIds.normalizeLang("es-419"))
        assertEquals("all", JsSourceIds.normalizeLang(""))
        assertEquals("all", JsSourceIds.normalizeLang("ALL"))
        assertEquals("all", JsSourceIds.normalizeLang("multi"))
        assertEquals("all", JsSourceIds.normalizeLang("*"))
        assertEquals("other", JsSourceIds.normalizeLang("other"))
    }

    @Test
    fun coverUrlsAreResolvedAgainstTheBaseUrl() {
        val base = "https://example.org/"
        assertNull(JsSourceUrls.resolveCoverUrl(base, null))
        assertNull(JsSourceUrls.resolveCoverUrl(base, "  "))
        assertEquals("https://cdn.example.org/a.jpg", JsSourceUrls.resolveCoverUrl(base, "https://cdn.example.org/a.jpg"))
        assertEquals("HTTP://cdn.example.org/a.jpg", JsSourceUrls.resolveCoverUrl(base, " HTTP://cdn.example.org/a.jpg "))
        // Only http(s) images: OkHttp and the cover fetchers cannot load other schemes, and local ones must not be read.
        assertNull(JsSourceUrls.resolveCoverUrl(base, "data:image/png;base64,AAAA"))
        assertNull(JsSourceUrls.resolveCoverUrl(base, "file:///etc/passwd"))
        assertNull(JsSourceUrls.resolveCoverUrl(base, "content://media/external/images/1"))
        assertNull(JsSourceUrls.resolveCoverUrl(base, "javascript:alert(1)"))
        assertNull(JsSourceUrls.resolveCoverUrl("file:///sdcard", "covers/a.jpg"))
        assertEquals("https://cdn.example.org/a.jpg", JsSourceUrls.resolveCoverUrl("file:///sdcard", "//cdn.example.org/a.jpg"))
        assertEquals("https://cdn.example.org/a.jpg", JsSourceUrls.resolveCoverUrl(base, "//cdn.example.org/a.jpg"))
        assertEquals("http://cdn.example.org/a.jpg", JsSourceUrls.resolveCoverUrl("http://example.org", "//cdn.example.org/a.jpg"))
        assertEquals("https://cdn.example.org/a.jpg", JsSourceUrls.resolveCoverUrl("", "//cdn.example.org/a.jpg"))
        assertEquals("https://example.org/covers/a.jpg", JsSourceUrls.resolveCoverUrl(base, "/covers/a.jpg"))
        assertEquals("https://example.org/covers/a.jpg", JsSourceUrls.resolveCoverUrl(base, "covers/a.jpg"))
        assertEquals("https://example.org/a.jpg?x=a:b", JsSourceUrls.resolveCoverUrl(base, "a.jpg?x=a:b"))
        assertNull(JsSourceUrls.resolveCoverUrl("", "covers/a.jpg"))
    }

    @Test
    fun webUrlsKeepUrlsAndPathsAndUseTheHomePageForOpaqueKeys() {
        val base = "https://example.org//"
        assertEquals("https://example.org", JsSourceUrls.normalizeBaseUrl(base))
        assertEquals("https://example.org/manga/1", JsSourceUrls.resolveWebUrl(base, "https://example.org/manga/1"))
        assertEquals("https://example.org/manga/1", JsSourceUrls.resolveWebUrl(base, "/manga/1"))
        assertEquals("https://other.org/x", JsSourceUrls.resolveWebUrl(base, "//other.org/x"))
        assertEquals("https://example.org", JsSourceUrls.resolveWebUrl(base, "8f3c-uuid"))
        assertEquals("https://example.org", JsSourceUrls.resolveWebUrl(base, "manga/1"))
        assertEquals("", JsSourceUrls.resolveWebUrl("", "/manga/1"))
    }

    @Test
    fun statusesMapToMihonCodes() {
        assertEquals(MihonMangaStatus.ONGOING, JsMangaStatus.ONGOING.toMihonStatus())
        assertEquals(MihonMangaStatus.COMPLETED, JsMangaStatus.COMPLETED.toMihonStatus())
        assertEquals(MihonMangaStatus.ON_HIATUS, JsMangaStatus.ON_HIATUS.toMihonStatus())
        assertEquals(MihonMangaStatus.CANCELLED, JsMangaStatus.CANCELLED.toMihonStatus())
        assertEquals(MihonMangaStatus.PUBLISHING_FINISHED, JsMangaStatus.PUBLISHING_FINISHED.toMihonStatus())
        assertEquals(MihonMangaStatus.UNKNOWN, JsMangaStatus.UNKNOWN.toMihonStatus())
        assertEquals(listOf(1, 2, 6, 5, 4, 0), (0..5).map { JsMangaStatus.fromCode(it).toMihonStatus() })
    }

    @Test
    fun pageRefsRoundTripThroughPageUrls() {
        val page = JsPageRef("https://img.example.org/1.jpg", mapOf("Referer" to "https://example.org/", "X-Token" to "a\"b"))
        val encoded = page.encode()
        assertTrue(encoded.startsWith(JsPageRef.PREFIX))
        assertEquals(page, JsPageRef.decode(encoded))
        assertEquals(JsPageRef("https://img.example.org/2.jpg"), JsPageRef.decode(JsPageRef("https://img.example.org/2.jpg").encode()))
        assertNull(JsPageRef.decode(null))
        assertNull(JsPageRef.decode(""))
        assertNull(JsPageRef.decode("https://img.example.org/1.jpg"))
        assertNull(JsPageRef.decode(JsPageRef.PREFIX + "{not json"))
        assertNull(JsPageRef.decode(JsPageRef.PREFIX + "{\"headers\":{}}"))
    }
}
