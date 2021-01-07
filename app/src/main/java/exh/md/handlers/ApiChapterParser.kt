package exh.md.handlers

import eu.kanade.tachiyomi.source.model.Page
import exh.md.handlers.serializers.ApiChapterSerializer
import exh.md.utils.MdUtil
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

class ApiChapterParser {
    fun pageListParse(response: Response): List<Page> {
        val jsonData = response.body!!.string()
        val networkApiChapter = MdUtil.jsonParser.decodeFromString<ApiChapterSerializer>(jsonData)

        val pages = mutableListOf<Page>()

        val hash = networkApiChapter.data.hash
        val pageArray = networkApiChapter.data.pages
        val server = networkApiChapter.data.server

        pageArray.forEach {
            val url = "$hash/$it"
            pages.add(Page(pages.size, "$server,${response.request.url},${System.currentTimeMillis()}", url))
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
