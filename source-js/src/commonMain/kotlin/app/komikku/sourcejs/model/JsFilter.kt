package app.komikku.sourcejs.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A search filter (contract 5.1). The list from `getFilterList()` is parsed into these; the UI changes `state`
 * through `copy(state = …)` and the list goes back to `search` with [toJson], in the original order.
 *
 * [raw] is the object the source returned. It is sent back with the canonical `type_name` and the current `state`
 * filled in, so source-specific keys survive the round trip (Mangayomi drops them; nothing relies on that).
 *
 * @property type the source's free id for the filter (`type` key), used by sources that look filters up by id.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed class JsFilter {
    abstract val name: String
    abstract val type: String?
    abstract val raw: JsonObject

    /** The JSON object `search` receives for this filter. */
    fun toJson(): JsonObject = buildJsonObject {
        raw.forEach { (key, value) -> put(key, value) }
        put("type_name", JsonPrimitive(typeName))
        stateJson()?.let { put("state", it) }
    }

    internal abstract val typeName: String

    internal abstract fun stateJson(): JsonElement?

    @Serializable
    @SerialName("header")
    data class Header(override val name: String, override val type: String?, override val raw: JsonObject) : JsFilter() {
        override val typeName get() = "HeaderFilter"
        override fun stateJson(): JsonElement? = null
    }

    @Serializable
    @SerialName("separator")
    data class Separator(override val name: String, override val type: String?, override val raw: JsonObject) : JsFilter() {
        override val typeName get() = "SeparatorFilter"
        override fun stateJson(): JsonElement? = null
    }

    @Serializable
    @SerialName("text")
    data class Text(
        override val name: String,
        override val type: String?,
        override val raw: JsonObject,
        val state: String = "",
    ) : JsFilter() {
        override val typeName get() = "TextFilter"
        override fun stateJson() = JsonPrimitive(state)
    }

    @Serializable
    @SerialName("checkbox")
    data class CheckBox(
        override val name: String,
        override val type: String?,
        override val raw: JsonObject,
        val value: String? = null,
        val state: Boolean = false,
    ) : JsFilter() {
        override val typeName get() = "CheckBox"
        override fun stateJson() = JsonPrimitive(state)
    }

    /** [state]: [STATE_IGNORE], [STATE_INCLUDE] or [STATE_EXCLUDE] (same numbering as Mihon's `Filter.TriState`). */
    @Serializable
    @SerialName("tristate")
    data class TriState(
        override val name: String,
        override val type: String?,
        override val raw: JsonObject,
        val value: String? = null,
        val state: Int = STATE_IGNORE,
    ) : JsFilter() {
        override val typeName get() = "TriState"
        override fun stateJson() = JsonPrimitive(state)

        companion object {
            const val STATE_IGNORE = 0
            const val STATE_INCLUDE = 1
            const val STATE_EXCLUDE = 2
        }
    }

    /** [state] is an index into [values]. */
    @Serializable
    @SerialName("select")
    data class Select(
        override val name: String,
        override val type: String?,
        override val raw: JsonObject,
        val values: List<JsSelectOption> = emptyList(),
        val state: Int = 0,
    ) : JsFilter() {
        override val typeName get() = "SelectFilter"
        override fun stateJson() = JsonPrimitive(state)
    }

    @Serializable
    @SerialName("sort")
    data class Sort(
        override val name: String,
        override val type: String?,
        override val raw: JsonObject,
        val values: List<JsSelectOption> = emptyList(),
        val state: JsSortState = JsSortState(),
    ) : JsFilter() {
        override val typeName get() = "SortFilter"
        override fun stateJson() = buildJsonObject {
            put("type_name", JsonPrimitive("SortState"))
            put("index", JsonPrimitive(state.index))
            put("ascending", JsonPrimitive(state.ascending))
        }
    }

    /** Its `state` in JSON is the array of child filters (in practice check boxes and tri-states). */
    @Serializable
    @SerialName("group")
    data class Group(
        override val name: String,
        override val type: String?,
        override val raw: JsonObject,
        val filters: List<JsFilter> = emptyList(),
    ) : JsFilter() {
        override val typeName get() = "GroupFilter"
        override fun stateJson() = JsonArray(filters.map { it.toJson() })
    }
}

/** An option of a select or sort filter: [name] is the label, [value] what the source reads. */
@Serializable
data class JsSelectOption(val name: String, val value: String)

@Serializable
data class JsSortState(val index: Int = 0, val ascending: Boolean = false)

/** The JSON array `search` receives for [filters]. */
fun List<JsFilter>.toJsonArray(): JsonArray = JsonArray(map { it.toJson() })
