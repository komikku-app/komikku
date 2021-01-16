package exh.md.handlers

import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.Page
import exh.md.handlers.serializers.ApiChapterSerializer
import exh.md.utils.MdUtil
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

class ApiChapterParser {
    // Only used in [PageHandler], which means its currently unused, kept for reference
    fun pageListParse(response: Response): List<Page> {
        val networkApiChapter = response.parseAs<ApiChapterSerializer>(MdUtil.jsonParser)

        val hash = networkApiChapter.data.hash
        val pageArray = networkApiChapter.data.pages
        val server = networkApiChapter.data.server

        return pageArray.mapIndexed { index, page ->
            val url = "$hash/$page"
            Page(index, "$server,${response.request.url},${System.currentTimeMillis()}", url)
        }
    }

    fun externalParse(response: Response): String {
        val jsonData = response.body!!.string()
        val json = Json.decodeFromString<JsonObject>(jsonData)
        val external = json["data"]!!.jsonObject["pages"]!!.jsonPrimitive.content
        return external.substringAfterLast("/")
    }
}
