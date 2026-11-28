package android.net

import app.komikku.desktop.compat.FileUris
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class UriTest {

    @Test
    fun `parses a full hierarchical URI`() {
        val uri = Uri.parse("https://user:pw@example.com:8080/a/b%20c/d.html?x=1&y=a+b&y=%C3%A9#frag%20ment")

        uri.scheme shouldBe "https"
        uri.isHierarchical shouldBe true
        uri.isAbsolute shouldBe true
        uri.encodedAuthority shouldBe "user:pw@example.com:8080"
        uri.userInfo shouldBe "user:pw"
        uri.host shouldBe "example.com"
        uri.port shouldBe 8080
        uri.encodedPath shouldBe "/a/b%20c/d.html"
        uri.path shouldBe "/a/b c/d.html"
        uri.pathSegments shouldContainExactly listOf("a", "b c", "d.html")
        uri.lastPathSegment shouldBe "d.html"
        uri.encodedQuery shouldBe "x=1&y=a+b&y=%C3%A9"
        uri.getQueryParameter("x") shouldBe "1"
        uri.getQueryParameter("y") shouldBe "a b"
        uri.getQueryParameters("y") shouldContainExactly listOf("a+b", "é")
        uri.queryParameterNames shouldContainExactly setOf("x", "y")
        uri.getQueryParameter("missing") shouldBe null
        uri.fragment shouldBe "frag ment"
        uri.encodedSchemeSpecificPart shouldBe "//user:pw@example.com:8080/a/b%20c/d.html?x=1&y=a+b&y=%C3%A9"
        uri.toString() shouldBe "https://user:pw@example.com:8080/a/b%20c/d.html?x=1&y=a+b&y=%C3%A9#frag%20ment"
    }

    @Test
    fun `handles missing parts`() {
        val uri = Uri.parse("http://example.com")
        uri.host shouldBe "example.com"
        uri.port shouldBe -1
        uri.path shouldBe ""
        uri.pathSegments.shouldBeEmpty()
        uri.lastPathSegment shouldBe null
        uri.query shouldBe null
        uri.fragment shouldBe null

        val queryOnly = Uri.parse("http://example.com?q=1")
        queryOnly.path shouldBe ""
        queryOnly.getQueryParameter("q") shouldBe "1"

        // A '?' after the fragment separator is part of the fragment.
        Uri.parse("http://example.com/p#f?x").query shouldBe null
    }

    @Test
    fun `parses opaque and relative URIs`() {
        val mail = Uri.parse("mailto:someone@example.com?subject=hi")
        mail.isOpaque shouldBe true
        mail.scheme shouldBe "mailto"
        mail.schemeSpecificPart shouldBe "someone@example.com?subject=hi"
        mail.path shouldBe null
        mail.host shouldBe null
        assertThrows<UnsupportedOperationException> { mail.getQueryParameter("subject") }

        val relative = Uri.parse("manga/123?page=2")
        relative.isRelative shouldBe true
        relative.scheme shouldBe null
        relative.path shouldBe "manga/123"
        relative.getQueryParameter("page") shouldBe "2"

        val schemeRelative = Uri.parse("//cdn.example.com/img.png")
        schemeRelative.host shouldBe "cdn.example.com"
        schemeRelative.path shouldBe "/img.png"

        val ipv6 = Uri.parse("http://[::1]/x")
        ipv6.host shouldBe "[::1]"
        ipv6.port shouldBe -1
    }

    @Test
    fun `builds URIs`() {
        val uri = Uri.Builder()
            .scheme("https")
            .authority("example.com")
            .appendPath("search")
            .appendPath("a b/c")
            .appendQueryParameter("q", "one piece & co")
            .appendQueryParameter("page", "2")
            .fragment("top")
            .build()

        uri.toString() shouldBe "https://example.com/search/a%20b%2Fc?q=one%20piece%20%26%20co&page=2#top"
        uri.getQueryParameter("q") shouldBe "one piece & co"
        uri.pathSegments shouldContainExactly listOf("search", "a b/c")

        val updated = Uri.parse("https://example.com/a?x=1#f").buildUpon()
            .appendEncodedPath("b")
            .appendQueryParameter("y", "2")
            .build()
        updated.toString() shouldBe "https://example.com/a/b?x=1&y=2#f"

        Uri.withAppendedPath(Uri.parse("content://auth"), "seg").toString() shouldBe "content://auth/seg"
        Uri.parse("https://example.com/a?x=1").buildUpon().clearQuery().build().toString() shouldBe
            "https://example.com/a"
        Uri.Builder().scheme("https").authority("h").path("rel").build().toString() shouldBe "https://h/rel"
        Uri.fromParts("tel", "+1 234", null).toString() shouldBe "tel:%2B1%20234"
        assertThrows<UnsupportedOperationException> { Uri.Builder().opaquePart("x").build() }
    }

    @Test
    fun `fromFile encodes the absolute path`() {
        val file = File("/tmp/some dir/ä.cbz")
        val uri = Uri.fromFile(file)
        uri.scheme shouldBe "file"
        uri.toString() shouldBe "file://" + Uri.encode(FileUris.uriPath(file.absolutePath), "/")
        uri.path shouldBe FileUris.uriPath(file.absolutePath)
        uri.encodedAuthority shouldBe ""
    }

    @Test
    fun `encode and decode`() {
        Uri.encode("a b&c/d?é") shouldBe "a%20b%26c%2Fd%3F%C3%A9"
        Uri.encode("a b/c", "/") shouldBe "a%20b/c"
        Uri.encode("_-!.~'()*") shouldBe "_-!.~'()*"
        Uri.decode("a%20b+c%C3%A9") shouldBe "a b+cé"
        Uri.decode("bad%zzescape") shouldBe "bad�escape"
        Uri.decode(Uri.encode("emoji 😀 and 日本")) shouldBe "emoji 😀 and 日本"
    }

    @Test
    fun `equality and normalization`() {
        Uri.parse("http://a/b") shouldBe Uri.parse("http://a/b")
        Uri.parse("HTTP://a/b").normalizeScheme().toString() shouldBe "http://a/b"
        Uri.parse("http://a/?flag=0").getBooleanQueryParameter("flag", true) shouldBe false
        Uri.parse("http://a/?flag=yes").getBooleanQueryParameter("flag", false) shouldBe true
        Uri.EMPTY.toString() shouldBe ""
    }
}
