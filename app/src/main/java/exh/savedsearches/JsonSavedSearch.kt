package exh.savedsearches

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class JsonSavedSearch(
    val name: String,
    val query: String,
    val filters: JsonArray
)
