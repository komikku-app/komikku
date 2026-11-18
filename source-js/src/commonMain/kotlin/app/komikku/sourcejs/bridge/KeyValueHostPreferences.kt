package app.komikku.sourcejs.bridge

import app.komikku.sourcejs.JsSourceDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** A plain string key/value store, e.g. an adapter over the app's `PreferenceStore`. */
interface JsKeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** [JsKeyValueStore] in memory, for tests and previews. */
class InMemoryKeyValueStore : JsKeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        values[key] = value
    }
}

/**
 * [HostPreferences] over a [JsKeyValueStore]. Keys are namespaced per source id
 * (`js_source_<id>_pref_<key>` for declared preferences, `js_source_<id>_str_<key>` for `getString` /
 * `setString`); typed values are stored as tagged strings.
 */
class KeyValueHostPreferences(
    private val store: JsKeyValueStore,
    private val sourceId: Long,
) : HostPreferences {

    override fun getValue(key: String): JsPreferenceValue? {
        val raw = store.get(prefKey(key)) ?: return null
        val payload = raw.substring(2.coerceAtMost(raw.length))
        return when (raw.take(2)) {
            "s:" -> JsPreferenceValue.Text(payload)
            "b:" -> JsPreferenceValue.Bool(payload == "true")
            "l:" -> runCatching {
                JsPreferenceValue.TextSet(Json.parseToJsonElement(payload).jsonArray.map { it.jsonPrimitive.content })
            }.getOrNull()
            else -> null
        }
    }

    override fun setValue(key: String, value: JsPreferenceValue) {
        val raw = when (value) {
            is JsPreferenceValue.Text -> "s:" + value.value
            is JsPreferenceValue.Bool -> "b:" + value.value
            is JsPreferenceValue.TextSet -> "l:" + JsonArray(value.values.map(::JsonPrimitive))
        }
        store.put(prefKey(key), raw)
    }

    override fun getString(key: String): String? = store.get("js_source_${sourceId}_str_$key")

    override fun setString(key: String, value: String) = store.put("js_source_${sourceId}_str_$key", value)

    private fun prefKey(key: String) = "js_source_${sourceId}_pref_$key"

    companion object {
        /** One store shared by all sources, namespaced by source id. */
        fun provider(store: JsKeyValueStore) = HostPreferencesProvider { source: JsSourceDescriptor ->
            KeyValueHostPreferences(store, source.id)
        }
    }
}
