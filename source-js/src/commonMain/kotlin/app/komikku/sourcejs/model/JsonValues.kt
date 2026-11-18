package app.komikku.sourcejs.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/*
 * Lenient JSON coercions shared by the bridge (arguments from the prelude) and the result parser. Missing values and
 * JSON `null` are always `null`.
 */

/** Text of a value: a primitive's content, a container's JSON text, `null` for missing / `null`. */
internal fun JsonElement?.textOrNull(): String? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> content
    else -> toString()
}

/** A finite number from a JSON number or a numeric string; anything else (incl. `"NaN"`) gives `null`. */
internal fun JsonElement?.numberOrNull(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return (primitive.doubleOrNull ?: primitive.content.trim().toDoubleOrNull())?.takeIf { it.isFinite() }
}

/** A JSON boolean (not a string), `null` otherwise. */
internal fun JsonElement?.jsonBooleanOrNull(): Boolean? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }?.booleanOrNull
