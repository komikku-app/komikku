package exh.md.network

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaDexAuthInterceptorTest {

    @Test
    fun `OAuth error body on 400 or 401 is a rejection`() {
        isRefreshRejected(400, """{"error":"invalid_grant","error_description":"Token is not active"}""") shouldBe true
        isRefreshRejected(401, """{"error":"invalid_client"}""") shouldBe true
        isRefreshRejected(400, """{"error":"not_allowed"}""") shouldBe true
    }

    @Test
    fun `non OAuth body on 400 is not a rejection`() {
        isRefreshRejected(400, "<html><body>Just a moment...</body></html>") shouldBe false
        isRefreshRejected(400, "") shouldBe false
    }

    @Test
    fun `JSON without a usable error field on 400 is not a rejection`() {
        isRefreshRejected(400, """{"error_description":"Token is not active"}""") shouldBe false
        isRefreshRejected(400, """{"error":""}""") shouldBe false
        isRefreshRejected(400, """{"error":"   "}""") shouldBe false
        isRefreshRejected(400, """{"error":400}""") shouldBe false
        isRefreshRejected(400, """{"error":null}""") shouldBe false
        isRefreshRejected(400, """{"error":{"code":"invalid_grant"}}""") shouldBe false
        isRefreshRejected(400, """["invalid_grant"]""") shouldBe false
    }

    @Test
    fun `OAuth error body on other status codes is not a rejection`() {
        val body = """{"error":"invalid_grant","error_description":"Token is not active"}"""
        isRefreshRejected(403, body) shouldBe false
        isRefreshRejected(429, body) shouldBe false
        isRefreshRejected(500, body) shouldBe false
        isRefreshRejected(503, body) shouldBe false
    }
}
