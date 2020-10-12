package exh.md.handlers

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import java.util.Date

class ApiChapterParser {
    fun pageListParse(response: Response): List<Page> {
        val jsonData = response.body!!.string()
        val json = Json.decodeFromString<JsonObject>(jsonData)

        val pages = mutableListOf<Page>()

        val hash = json["hash"]!!.jsonPrimitive.content
        val pageArray = json["page_array"]!!.jsonArray
        val server = json["server"]!!.jsonPrimitive.content

        pageArray.forEach {
            val url = "$hash/${it.jsonPrimitive.content}"
            pages.add(Page(pages.size, "$server,${response.request.url},${Date().time}", url))
        }

        return pages
    }

    fun externalParse(response: Response): String {
        val jsonData = response.body!!.string()
        val json = Json.decodeFromString<JsonObject>(jsonData)
        val external = json["external"]!!.jsonPrimitive.content
        return external.substringAfterLast("/")
    }
}
