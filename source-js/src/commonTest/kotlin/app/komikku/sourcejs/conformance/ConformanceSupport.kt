package app.komikku.sourcejs.conformance

import app.komikku.sourcejs.TestHost
import app.komikku.sourcejs.provider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertEquals

/*
 * Bridge conformance suite: the executable form of docs/kmp-desktop/MANGAYOMI_CONTRACT.md section 4. Each fixture
 * is the JavaScript text of an extension file whose global `async function conformance(provider)` exercises one
 * bridge area and returns an object of results; the tests assert those results exactly. The fixtures are Kotlin
 * strings (not resources) so the same suite runs unchanged on every target, including iOS later.
 */

/** Runs [fixture] in a fresh extension and returns what its `conformance(provider)` resolved to. */
internal suspend fun TestHost.runConformance(fixture: String, extraMembers: String = ""): JsonObject {
    val extension = extension(
        provider(
            header = fixture,
            body = """
                $extraMembers
                async getPopular(page) {
                  return { list: [{ name: JSON.stringify(await conformance(this)), link: "x" }], hasNextPage: false };
                }
            """,
        ),
    )
    return Json.parseToJsonElement(extension.getPopular(1).list.single().name).jsonObject
}

/** Compares JSON structurally (key order of objects ignored). */
internal fun assertJson(expected: String, actual: JsonElement?, message: String? = null) {
    assertEquals(Json.parseToJsonElement(expected), actual, message)
}
