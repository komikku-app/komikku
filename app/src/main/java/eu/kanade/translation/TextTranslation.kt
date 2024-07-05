package eu.kanade.translation

import kotlinx.serialization.Serializable

@Serializable
data class TextTranslation(
    var text: String,
    var width: Float,
    var height: Float,
    var x: Float,
    var y: Float,
    val angle: Float,
    var translated: String = "",
)
