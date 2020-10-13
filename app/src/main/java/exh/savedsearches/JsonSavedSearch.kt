package exh.savedsearches

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class JsonSavedSearch(
    val name: String,
    val query: String,
    val filters: JsonArray
) {
    companion object {
        fun fromJsonObject(json: JsonObject): JsonSavedSearch {
            return JsonSavedSearch(
                json["name"]!!.jsonPrimitive.content,
                json["query"]!!.jsonPrimitive.content,
                json["filters"]!!.jsonArray,
            )
        }
    }
}
