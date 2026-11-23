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

        /** The `repo.json` URL for the index at [indexUrl]: the index URL without its file name, query and fragment. */
        fun infoUrl(indexUrl: String): String =
            indexUrl.substringBefore('#').substringBefore('?').substringBeforeLast('/') + "/repo.json"

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
