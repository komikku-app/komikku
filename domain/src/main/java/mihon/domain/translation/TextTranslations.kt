package mihon.domain.translation

import kotlinx.serialization.Serializable

@Serializable
data class TextTranslations(
    var translations: ArrayList<BlockTranslation> = ArrayList(),
    var imgWidth: Float = 0f,
    var imgHeight: Float = 0f,

) {
    companion object {
        val EMPTY = TextTranslations()
    }
}

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
