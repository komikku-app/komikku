package app.komikku.sourcejs.repo

import app.komikku.sourcejs.JsSourceDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** The index text was no Mangayomi legacy index (not JSON, or not a JSON array). */
class MangayomiIndexException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A parsed Mangayomi manga index (contract 1.1 / 1.2): the JavaScript manga entries the host can run, by id.
 *
 * @property entries the kept entries, at most one per id (the highest version; the first one on a tie), in index order.
 * @property skipped entries that were not kept: other item types or code languages, entries for a newer app,
 *   entries without an id, a name or a code URL, and objects that are not entries at all.
 */
data class MangayomiIndex(val entries: List<JsSourceDescriptor>, val skipped: Int) {

    companion object {
        /**
         * The Mangayomi version the contract describes (MANGAYOMI_CONTRACT.md); entries whose `appMinVerReq` is above
         * it need an app this host does not follow and are skipped, as Mangayomi hides them.
         */
        const val TARGET_APP_VERSION = "0.9.96"

        /** `sourceCodeLanguage` of JavaScript entries. */
        const val JAVASCRIPT = 1

        /** `itemType` of manga entries. */
        const val MANGA = 0

        private val json = Json { isLenient = true }

        /**
         * Parses the legacy JSON array index [text] (a body starting with `[`). Unknown fields are ignored, missing
         * optional fields take their defaults, and fields of an unexpected JSON type are read where that is
         * unambiguous (`"12"` as a number, `"true"` as a boolean) and treated as missing otherwise, so one odd entry
         * does not lose the others.
         *
         * @throws MangayomiIndexException when [text] is not a JSON array (malformed JSON, or another store format).
         */
        fun parse(text: String): MangayomiIndex {
            val root = try {
                json.parseToJsonElement(text.trimStart('﻿'))
            } catch (e: IllegalArgumentException) {
                // kotlinx.serialization reports malformed JSON as a SerializationException, an IllegalArgumentException.
                throw MangayomiIndexException("Not a JSON document: ${e.message}", e)
            }
            if (root !is JsonArray) throw MangayomiIndexException("Not a Mangayomi index (expected a JSON array)")
            val byId = LinkedHashMap<Long, JsSourceDescriptor>()
            var skipped = 0
            root.forEach { element ->
                val entry = (element as? JsonObject)?.let(::entryOf)
                if (entry == null) {
                    skipped++
                    return@forEach
                }
                val other = byId[entry.id]
                if (other != null) skipped++
                if (other == null || MangayomiVersions.compare(entry.version, other.version) > 0) byId[entry.id] = entry
            }
            return MangayomiIndex(byId.values.toList(), skipped)
        }

        /** The entry [obj] describes, or `null` when the host cannot or must not offer it. */
        internal fun entryOf(obj: JsonObject): JsSourceDescriptor? {
            val id = obj.long("id") ?: return null
            val name = obj.string("name")?.trim().orEmpty()
            val codeUrl = obj.string("sourceCodeUrl")?.trim().orEmpty()
            // Mangayomi: a missing code language is Dart (0); a missing item type follows the legacy `isManga`.
            val language = obj.long("sourceCodeLanguage") ?: 0L
            val itemType = obj.long("itemType") ?: if (obj.boolean("isManga") == false) 1L else MANGA.toLong()
            val minVersion = obj.string("appMinVerReq")?.trim().orEmpty()
            if (name.isEmpty() || codeUrl.isEmpty()) return null
            if (language != JAVASCRIPT.toLong() || itemType != MANGA.toLong()) return null
            if (!MangayomiVersions.isAtMost(minVersion, TARGET_APP_VERSION)) return null
            return JsSourceDescriptor(
                id = id,
                name = name,
                lang = obj.string("lang")?.trim().orEmpty(),
                baseUrl = obj.string("baseUrl")?.trim().orEmpty(),
                apiUrl = obj.string("apiUrl")?.trim().orEmpty(),
                iconUrl = obj.string("iconUrl")?.trim().orEmpty(),
                typeSource = obj.string("typeSource").orEmpty(),
                itemType = itemType.toInt(),
                sourceCodeLanguage = language.toInt(),
                sourceCodeUrl = codeUrl,
                version = obj.string("version")?.trim().orEmpty(),
                appMinVerReq = minVersion,
                isNsfw = obj.boolean("isNsfw") ?: false,
                hasCloudflare = obj.boolean("hasCloudflare") ?: false,
                isFullData = obj.boolean("isFullData") ?: false,
                dateFormat = obj.string("dateFormat").orEmpty(),
                dateFormatLocale = obj.string("dateFormatLocale").orEmpty(),
                additionalParams = obj.string("additionalParams").orEmpty(),
                notes = obj.string("notes")?.trim().orEmpty(),
            )
        }

        private fun JsonObject.primitive(key: String): JsonPrimitive? =
            (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }

        private fun JsonObject.string(key: String): String? = primitive(key)?.content

        private fun JsonObject.long(key: String): Long? {
            val value = primitive(key) ?: return null
            return value.longOrNull ?: value.content.trim().toLongOrNull()
                ?: value.content.trim().toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 && it in LONG_RANGE }?.toLong()
        }

        private fun JsonObject.boolean(key: String): Boolean? {
            val value = primitive(key) ?: return null
            value.booleanOrNull?.let { return it }
            return when (value.content.trim().lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> null
            }
        }

        /** Doubles that are exact integers (JSON numbers written as `1.0E9`). */
        private val LONG_RANGE = -9.0E15..9.0E15
    }
}
