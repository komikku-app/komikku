package exh.md.handlers

import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.source.model.Page
import java.util.Date
import okhttp3.Response

class ApiChapterParser {
    fun pageListParse(response: Response): List<Page> {
        val jsonData = response.body!!.string()
        val json = JsonParser.parseString(jsonData).asJsonObject

        val pages = mutableListOf<Page>()

        val hash = json.get("hash").string
        val pageArray = json.getAsJsonArray("page_array")
        val server = json.get("server").string

        pageArray.forEach {
            val url = "$hash/${it.asString}"
            pages.add(Page(pages.size, "$server,${response.request.url},${Date().time}", url))
        }

        return pages
    }

    fun externalParse(response: Response): String {
        val jsonData = response.body!!.string()
        val json = JsonParser.parseString(jsonData).asJsonObject
        val external = json.get("external").string
        return external.substringAfterLast("/")
    }
}
