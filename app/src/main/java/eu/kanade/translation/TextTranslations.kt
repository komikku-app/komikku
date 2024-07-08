package eu.kanade.translation

import kotlinx.serialization.Serializable

@Serializable
data class TextTranslations(
    var translations: ArrayList<BlockTranslation> = ArrayList(),
    var imgWidth: Float,
    var imgHeight: Float,
)

@Serializable
data class BlockTranslation(
    var text: String,
    var width: Float,
    var height: Float,
    var x: Float,
    var y: Float,
    var symHeight: Float,
    var symWidth: Float,
    val angle: Float,
    var translated: String = "",
)
