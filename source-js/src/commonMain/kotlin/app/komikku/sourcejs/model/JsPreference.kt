package app.komikku.sourcejs.model

import app.komikku.sourcejs.bridge.JsPreferenceValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * A source preference declared by `getSourcePreferences()` (contract 5.2). The declared values are defaults; the
 * user's choice is stored through `HostPreferences` and read back by the script's `SharedPreferences.get(key)`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed class JsPreference {
    abstract val key: String
    abstract val title: String
    abstract val summary: String

    /** The value `SharedPreferences.get(key)` returns while nothing is stored. */
    abstract val defaultValue: JsPreferenceValue

    @Serializable
    @SerialName("checkbox")
    data class CheckBox(
        override val key: String,
        override val title: String = "",
        override val summary: String = "",
        val value: Boolean = false,
    ) : JsPreference() {
        override val defaultValue get() = JsPreferenceValue.Bool(value)
    }

    @Serializable
    @SerialName("switch")
    data class Switch(
        override val key: String,
        override val title: String = "",
        override val summary: String = "",
        val value: Boolean = false,
    ) : JsPreference() {
        override val defaultValue get() = JsPreferenceValue.Bool(value)
    }

    /** [valueIndex] is clamped to ≥ 0; the stored value is the selected entry value. */
    @Serializable
    @SerialName("list")
    data class List(
        override val key: String,
        override val title: String = "",
        override val summary: String = "",
        val entries: kotlin.collections.List<String> = emptyList(),
        val entryValues: kotlin.collections.List<String> = emptyList(),
        val valueIndex: Int = 0,
    ) : JsPreference() {
        override val defaultValue
            get() = JsPreferenceValue.Text(entryValues.getOrNull(valueIndex) ?: entryValues.firstOrNull().orEmpty())
    }

    /** [values] are the selected entry values. */
    @Serializable
    @SerialName("multiselect")
    data class MultiSelect(
        override val key: String,
        override val title: String = "",
        override val summary: String = "",
        val entries: kotlin.collections.List<String> = emptyList(),
        val entryValues: kotlin.collections.List<String> = emptyList(),
        val values: kotlin.collections.List<String> = emptyList(),
    ) : JsPreference() {
        override val defaultValue get() = JsPreferenceValue.TextSet(values)
    }

    @Serializable
    @SerialName("edittext")
    data class EditText(
        override val key: String,
        override val title: String = "",
        override val summary: String = "",
        val value: String = "",
        val dialogTitle: String = "",
        val dialogMessage: String = "",
        val text: String = "",
    ) : JsPreference() {
        override val defaultValue get() = JsPreferenceValue.Text(value)
    }
}
