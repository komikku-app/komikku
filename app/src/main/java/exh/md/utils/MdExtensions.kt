package exh.md.utils

import exh.md.dto.ListCallDto
import exh.util.under
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

suspend fun <T> mdListCall(request: suspend (offset: Int) -> ListCallDto<T>): List<T> {
    val results = mutableListOf<T>()
    var offset = 0

    do {
        val list = request(offset)
        results += list.results
        offset += list.limit
    } while (offset under list.total)

    return results
}

fun JsonElement.asMdMap(): Map<String, String> {
    return runCatching {
        jsonObject.map { it.key to it.value.jsonPrimitive.contentOrNull.orEmpty() }.toMap()
    }.getOrElse { emptyMap() }
}
