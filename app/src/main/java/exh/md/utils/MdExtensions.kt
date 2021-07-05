package exh.md.utils

import exh.md.dto.ListCallDto
import exh.util.under

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
