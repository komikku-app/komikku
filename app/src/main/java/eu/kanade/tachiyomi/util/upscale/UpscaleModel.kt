package eu.kanade.tachiyomi.util.upscale

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.kmk.KMR

data class BatchVariant(
    val batchSize: Int,
    val assetFileName: String,
)

/**
 * Describes an available upscaling model. 'offset' si the quantity of
 * pixels "eaten" by the network due to convolutions without internal padding
 * (valid convolution), 0 for same-padding models like Real-ESRGAN, >0 for
 * models like waifu2x that narrow the tile across the network:
 * real_output = scale * tileContentSize
 * input_to_give_to_the_network = tileContentSize + offset / scale
 */
enum class UpscaleModel(
    val displayName: String,
    val descriptionRes: StringResource,
    val scale: Int,
    val tileContentSize: Int,
    val offset: Int,
    val batchVariants: List<BatchVariant>,
) {
    REALESRGAN_ANIMEVIDEOV3(
        displayName = "Real-ESRGAN (4x)",
        descriptionRes = KMR.strings.desc_upscale_realesrgan_animevideov3,
        scale = 4,
        tileContentSize = 384,
        offset = 0,
        batchVariants = listOf(
            BatchVariant(batchSize = 1, assetFileName = "realesr_animevideov3_x4_384T_B1_float32.tflite"),
            BatchVariant(batchSize = 3, assetFileName = "realesr_animevideov3_x4_384T_B3_float32.tflite"),
        ),
    ),
    WAIFU2X_SCALE2X(
        displayName = "waifu2x (2x, no denoise)",
        descriptionRes = KMR.strings.desc_upscale_waifu2x,
        scale = 2,
        tileContentSize = 384,
        offset = 28,
        batchVariants = listOf(
            BatchVariant(batchSize = 1, assetFileName = "waifu2x_384T_B1_float32.tflite"),
            BatchVariant(batchSize = 3, assetFileName = "waifu2x_384T_B3_float32.tflite"),
        ),
    ),
    REALCUGAN_SCALE2X(
        displayName = "Real-CUGAN (2x)",
        descriptionRes = KMR.strings.desc_upscale_realcugan2x,
        scale = 2,
        tileContentSize = 384,
        offset = 72,
        batchVariants = listOf(
            BatchVariant(batchSize = 1, assetFileName = "realcugan2x_no_denoise_384T_B1_float32.tflite"),
            BatchVariant(batchSize = 3, assetFileName = "realcugan2x_no_denoise_384T_B3_float32.tflite"),
        ),
    ),
    REALCUGAN_SCALE3X(
        displayName = "Real-CUGAN (3x)",
        descriptionRes = KMR.strings.desc_upscale_realcugan3x,
        scale = 3,
        tileContentSize = 384,
        offset = 84, // = paddingPerSide(14) × 2 × scale(3)
        batchVariants = listOf(
            BatchVariant(batchSize = 1, assetFileName = "realcugan3x_no_denoise_384T_B1_float32.tflite"),
            BatchVariant(batchSize = 3, assetFileName = "realcugan3x_no_denoise_384T_B3_float32.tflite"),
        ),
    ),
    ;

    init {
        require(offset % (2 * scale) == 0) {
            "offset ($offset) must be divisible for 2*scale ($scale) in $name"
        }
        require(batchVariants.isNotEmpty()) { "$name must have at leat one batch variant" }
        require(batchVariants.map { it.batchSize }.distinct().size == batchVariants.size) {
            "$name has duplicated batch sizes among variants"
        }
    }

    /** Padding pixels to add per side when a tile is extracted from the source page. */
    val paddingPerSide: Int get() = offset / (2 * scale)

    /** Real tile dimensions to give in input to the model (content + padding). */
    val paddedTileSize: Int get() = tileContentSize + 2 * paddingPerSide

    /** Produced output dimension of the model per a single tile. */
    val outSize: Int get() = scale * tileContentSize

    /** Batch sizes available in UI for this model. */
    val availableBatchSizes: List<Int> get() = batchVariants.map { it.batchSize }.sorted()

    /** Exact variant if it exists, otherwise the closest available one (never crashes on a stale value in preferences). */
    fun variantFor(requestedBatchSize: Int): BatchVariant =
        batchVariants.find { it.batchSize == requestedBatchSize }
            ?: batchVariants.minBy { kotlin.math.abs(it.batchSize - requestedBatchSize) }
}
