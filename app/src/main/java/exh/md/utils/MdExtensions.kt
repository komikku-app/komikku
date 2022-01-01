package exh.md.utils

import exh.md.dto.ListCallDto
import exh.util.under
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

suspend fun <T> mdListCall(request: suspend (offset: Int) -> ListCallDto<T>): List<T> {
    val results = mutableListOf<T>()
    var offset = 0

    do {
        val list = request(offset)
        results += list.data
        offset += list.limit
    } while (offset under list.total)

    return results
}

inline fun <reified T> JsonElement.asMdMap(): Map<String, T> {
    return runCatching {
        MdUtil.jsonParser.decodeFromJsonElement<Map<String, T>>(jsonObject)
    }.getOrElse { emptyMap() }
}
