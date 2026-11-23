package app.komikku.sourcejs.repo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The optional `repo.json` next to a Mangayomi index (contract 1.1): `{ "name", "website", "index_v2" }`, or the same
 * under `meta`. Only labels the repository; `index_v2` (another store format) is kept for information only.
 */
data class MangayomiRepoInfo(val name: String?, val website: String?, val indexV2: String?) {

    companion object {
        private val json = Json { isLenient = true }

        /**
         * The `repo.json` URL for the index at [indexUrl]: `repo.json` resolved against it (RFC 3986 5.2), so the
         * index's file name, query and fragment are dropped and an index URL without a path gives `/repo.json` on its
         * host; a trailing slash names a directory.
         */
        fun infoUrl(indexUrl: String): String {
            val url = indexUrl.trim().substringBefore('#').substringBefore('?')
            val authorityStart = url.indexOf("://").let { if (it < 0) -1 else it + 3 }
            val pathStart = if (authorityStart < 0) 0 else url.indexOf('/', authorityStart)
            return if (pathStart < 0) {
                "$url/repo.json"
            } else {
                url.substring(0, maxOf(url.lastIndexOf('/') + 1, pathStart)) + "repo.json"
            }
        }

        /** The info in [text], or `null` when it is not a JSON object (a missing or broken `repo.json` is no error). */
        fun parse(text: String): MangayomiRepoInfo? {
            val root = runCatching { json.parseToJsonElement(text.trimStart('﻿')) }.getOrNull() as? JsonObject
                ?: return null
            val obj = root["meta"] as? JsonObject ?: root
            fun field(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }
            return MangayomiRepoInfo(name = field("name"), website = field("website"), indexV2 = field("index_v2"))
        }
    }
}
