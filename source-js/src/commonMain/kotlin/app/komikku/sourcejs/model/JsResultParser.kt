package app.komikku.sourcejs.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** A result that does not fit the expected shape at [field]. */
internal class MalformedResultFailure(val field: String, val problem: String) : Exception("$field: $problem")

/**
 * Lenient decoding of provider results (contract 3, 5). Unknown keys are ignored, missing optional values take
 * defaults, numbers and booleans are accepted where strings are expected (and numeric strings where numbers are),
 * `null` list entries are skipped. A container of the wrong type (e.g. `list` not an array) or a value that cannot
 * be coerced (an object where a string is expected) raises [MalformedResultFailure] naming the field.
 */
internal object JsResultParser {

    fun mangaPage(result: JsonElement?): JsMangaPage {
        val root = result.requireObject("(result)")
        val list = root["list"].arrayOrEmpty("list").mapIndexedNotNull { index, item ->
            if (item is JsonNull) null else manga(item, "list[$index]")
        }
        return JsMangaPage(list = list, hasNextPage = root["hasNextPage"].booleanOrDefault("hasNextPage", false))
    }

    fun mangaDetail(result: JsonElement?): JsManga = manga(result, "(result)")

    fun pages(result: JsonElement?): List<JsPage> {
        val pages = result.requireArray("(result)").mapIndexedNotNull { index, entry ->
            val field = "[$index]"
            when (entry) {
                is JsonNull -> null
                is JsonPrimitive -> JsPage(entry.string(field).orEmpty().trim())
                is JsonObject -> JsPage(
                    url = entry["url"].string("$field.url").orEmpty().trim(),
                    headers = entry["headers"].stringMap("$field.headers"),
                )
                is JsonArray -> throw MalformedResultFailure(field, "expected a URL string or {url, headers}")
            }
        }
        return pages.filter { it.url.isNotEmpty() }.distinctBy { it.url }
    }

    fun headers(result: JsonElement?): Map<String, String> = result.stringMap("(result)")

    fun boolean(result: JsonElement?, default: Boolean): Boolean = result.booleanOrDefault("(result)", default)

    fun filters(result: JsonElement?): List<JsFilter> =
        result.arrayOrEmpty("(result)").mapIndexedNotNull { index, item -> filter(item, "[$index]") }

    fun preferences(result: JsonElement?): List<JsPreference> =
        result.arrayOrEmpty("(result)").mapIndexedNotNull { index, item -> preference(item, "[$index]") }

    private fun manga(element: JsonElement?, path: String): JsManga {
        val obj = element.requireObject(path)
        val chapters = (obj["chapters"]?.takeUnless { it is JsonNull } ?: obj["episodes"])
            .arrayOrEmpty("$path.chapters")
            .mapIndexedNotNull { index, item -> chapter(item, "$path.chapters[$index]") }
        return JsManga(
            name = obj["name"].string("$path.name").orEmpty(),
            link = obj["link"].string("$path.link").orEmpty(),
            imageUrl = obj["imageUrl"].string("$path.imageUrl").nullIfBlank(),
            description = obj["description"].string("$path.description").nullIfBlank(),
            author = obj["author"].string("$path.author").nullIfBlank(),
            artist = obj["artist"].string("$path.artist").nullIfBlank(),
            status = JsMangaStatus.fromCode(obj["status"].number()?.toInt()),
            genre = genre(obj["genre"], "$path.genre"),
            chapters = chapters,
        )
    }

    private fun genre(element: JsonElement?, path: String): List<String> {
        val values = when (element) {
            null, is JsonNull -> emptyList()
            is JsonPrimitive -> element.string(path).orEmpty().split(',')
            is JsonArray -> element.mapIndexedNotNull { index, item -> item.string("$path[$index]") }
            is JsonObject -> throw MalformedResultFailure(path, "expected an array of strings")
        }
        return values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun chapter(element: JsonElement, path: String): JsChapter? {
        if (element is JsonNull) return null
        val obj = element.requireObject(path)
        val url = obj["url"].string("$path.url").orEmpty()
        if (url.isEmpty()) return null
        return JsChapter(
            name = obj["name"].string("$path.name").orEmpty(),
            url = url,
            dateUpload = obj["dateUpload"].number()?.takeIf { it.isFinite() }?.toLong(),
            scanlator = obj["scanlator"].string("$path.scanlator").nullIfBlank(),
        )
    }

    private fun filter(element: JsonElement, path: String): JsFilter? {
        if (element !is JsonObject) {
            if (element is JsonNull) return null
            throw MalformedResultFailure(path, "expected a filter object")
        }
        val name = element["name"].string("$path.name").orEmpty()
        val type = element["type"].string("$path.type")
        val state = element["state"]
        return when (element["type_name"].string("$path.type_name")) {
            "HeaderFilter" -> JsFilter.Header(name, type, element)
            "SeparatorFilter" -> JsFilter.Separator(name, type, element)
            "TextFilter" -> JsFilter.Text(name, type, element, state.string("$path.state").orEmpty())
            "CheckBox" -> JsFilter.CheckBox(
                name,
                type,
                element,
                value = element["value"].string("$path.value"),
                state = state.booleanOrDefault("$path.state", false),
            )
            "TriState" -> JsFilter.TriState(
                name,
                type,
                element,
                value = element["value"].string("$path.value"),
                state = state.number()?.toInt()?.takeIf { it in 0..2 } ?: JsFilter.TriState.STATE_IGNORE,
            )
            "SelectFilter" -> JsFilter.Select(
                name,
                type,
                element,
                values = options(element["values"], "$path.values"),
                state = state.number()?.toInt()?.coerceAtLeast(0) ?: 0,
            )
            "SortFilter" -> JsFilter.Sort(
                name,
                type,
                element,
                values = options(element["values"], "$path.values"),
                state = (state as? JsonObject)?.let {
                    JsSortState(
                        index = it["index"].number()?.toInt()?.coerceAtLeast(0) ?: 0,
                        ascending = it["ascending"].booleanOrDefault("$path.state.ascending", false),
                    )
                } ?: JsSortState(),
            )
            "GroupFilter" -> JsFilter.Group(
                name,
                type,
                element,
                filters = state.arrayOrEmpty("$path.state").mapIndexedNotNull { index, child ->
                    filter(child, "$path.state[$index]")
                },
            )
            // Unknown kinds are dropped, as Mangayomi does.
            else -> null
        }
    }

    private fun options(element: JsonElement?, path: String): List<JsSelectOption> =
        element.arrayOrEmpty(path).mapIndexedNotNull { index, item ->
            when (item) {
                is JsonNull -> null
                is JsonObject -> JsSelectOption(
                    name = item["name"].string("$path[$index].name").orEmpty(),
                    value = item["value"].string("$path[$index].value").orEmpty(),
                )
                is JsonPrimitive -> item.string("$path[$index]").orEmpty().let { JsSelectOption(it, it) }
                is JsonArray -> throw MalformedResultFailure("$path[$index]", "expected a SelectOption object")
            }
        }

    private fun preference(element: JsonElement, path: String): JsPreference? {
        if (element !is JsonObject) {
            if (element is JsonNull) return null
            throw MalformedResultFailure(path, "expected a preference object")
        }
        val key = element["key"].string("$path.key").orEmpty()
        if (key.isEmpty()) return null
        fun body(field: String): JsonObject? = element[field]?.takeUnless { it is JsonNull }?.requireObject("$path.$field")
        body("checkBoxPreference")?.let { p ->
            return JsPreference.CheckBox(key, p.title(path), p.summary(path), p["value"].booleanOrDefault("$path.value", false))
        }
        body("switchPreferenceCompat")?.let { p ->
            return JsPreference.Switch(key, p.title(path), p.summary(path), p["value"].booleanOrDefault("$path.value", false))
        }
        body("listPreference")?.let { p ->
            return JsPreference.List(
                key,
                p.title(path),
                p.summary(path),
                entries = p["entries"].strings("$path.entries"),
                entryValues = p["entryValues"].strings("$path.entryValues"),
                valueIndex = p["valueIndex"].number()?.toInt()?.coerceAtLeast(0) ?: 0,
            )
        }
        body("multiSelectListPreference")?.let { p ->
            return JsPreference.MultiSelect(
                key,
                p.title(path),
                p.summary(path),
                entries = p["entries"].strings("$path.entries"),
                entryValues = p["entryValues"].strings("$path.entryValues"),
                values = p["values"].strings("$path.values"),
            )
        }
        body("editTextPreference")?.let { p ->
            return JsPreference.EditText(
                key,
                p.title(path),
                p.summary(path),
                value = p["value"].string("$path.value").orEmpty(),
                dialogTitle = p["dialogTitle"].string("$path.dialogTitle").orEmpty(),
                dialogMessage = p["dialogMessage"].string("$path.dialogMessage").orEmpty(),
                text = p["text"].string("$path.text").orEmpty(),
            )
        }
        // Unknown preference kinds are dropped.
        return null
    }

    private fun JsonObject.title(path: String) = this["title"].string("$path.title").orEmpty()

    private fun JsonObject.summary(path: String) = this["summary"].string("$path.summary").orEmpty()

    private fun JsonElement?.requireObject(path: String): JsonObject =
        this as? JsonObject ?: throw MalformedResultFailure(path, "expected an object, got ${describe()}")

    private fun JsonElement?.requireArray(path: String): JsonArray =
        this as? JsonArray ?: throw MalformedResultFailure(path, "expected an array, got ${describe()}")

    private fun JsonElement?.arrayOrEmpty(path: String): JsonArray = when (this) {
        null, is JsonNull -> JsonArray(emptyList())
        else -> requireArray(path)
    }

    /** String value; numbers and booleans are converted, `null` / missing give `null`. */
    private fun JsonElement?.string(path: String): String? = when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> content
        else -> throw MalformedResultFailure(path, "expected a string, got ${describe()}")
    }

    private fun JsonElement?.strings(path: String): List<String> =
        arrayOrEmpty(path).mapIndexedNotNull { index, item -> item.string("$path[$index]") }

    /** Number from a JSON number or a numeric string; anything else (incl. `"NaN"`) gives `null`. */
    private fun JsonElement?.number(): Double? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.longOrNull?.toDouble()
            ?: primitive.doubleOrNull?.takeIf { it.isFinite() }
            ?: primitive.content.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun JsonElement?.booleanOrDefault(path: String, default: Boolean): Boolean = when (this) {
        null, is JsonNull -> default
        is JsonPrimitive -> booleanOrNull ?: when (content.trim().lowercase()) {
            "true", "1" -> true
            "false", "0", "" -> false
            else -> throw MalformedResultFailure(path, "expected a boolean, got $this")
        }
        else -> throw MalformedResultFailure(path, "expected a boolean, got ${describe()}")
    }

    private fun JsonElement?.stringMap(path: String): Map<String, String> = when (this) {
        null, is JsonNull -> emptyMap()
        is JsonObject -> entries.mapNotNull { (key, value) -> value.string("$path.$key")?.let { key to it } }.toMap()
        else -> throw MalformedResultFailure(path, "expected an object, got ${describe()}")
    }

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    private fun JsonElement?.describe(): String = when (this) {
        null -> "nothing"
        is JsonNull -> "null"
        is JsonObject -> "an object"
        is JsonArray -> "an array"
        is JsonPrimitive -> if (isString) "a string" else "$this"
    }
}
