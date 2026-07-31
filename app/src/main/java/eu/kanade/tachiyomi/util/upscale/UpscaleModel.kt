package eu.kanade.tachiyomi.util.upscale

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.kmk.KMR


data class BatchVariant(
    val batchSize: Int,
    val assetFileName: String,
    // Popolati quando costruiamo il download manager (prossimo punto):
    // val downloadUrl: String, val sha256: String, val sizeBytes: Long
)
/**
 * Descrive un modello di upscaling disponibile. `offset` è la quantità di
 * pixel "mangiati" dalla rete per via di convoluzioni senza padding interno
 * (valid convolution) — 0 per modelli same-padding come Real-ESRGAN, >0 per
 * modelli come waifu2x/upconv_7 che restringono il tile attraversando la rete:
 *   output_reale = scale × tileContentSize
 *   input_da_dare_alla_rete = tileContentSize + offset / scale
 *
 * Per aggiungere un nuovo modello in futuro: converti, misura l'offset reale
 * (vedi verifyOffset in fondo al file per come derivarlo empiricamente se non
 * lo conosci dall'architettura), e aggiungi una entry qui — nient'altro nel
 * resto della classe AiUpscaler richiede modifiche.
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
            BatchVariant(batchSize = 3, assetFileName = "realesr_animevideov3_x4_384T_B3_float32.tflite")
        )
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
    CUNET_SCALE2X(
        displayName = "cunet (2x)",
        descriptionRes = KMR.strings.desc_upscale_cunet2x,
        scale = 2,
        tileContentSize = 384,
        offset = 72,
        batchVariants = listOf(
            BatchVariant(batchSize = 1, assetFileName = "cunet2x_384T_B1_float32.tflite")
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
            BatchVariant(batchSize = 3, assetFileName = "realcugan2x_no_denoise_384T_B3_float32.tflite")
        ),
    ),
    REALCUGAN_SCALE3X(
        displayName = "Real-CUGAN (3x)",
        descriptionRes = KMR.strings.desc_upscale_realcugan3x,
        scale = 3,
        tileContentSize = 384,
        offset = 84,   // = paddingPerSide(14) × 2 × scale(3)
        batchVariants = listOf(
            BatchVariant(batchSize = 1, assetFileName = "realcugan3x_no_denoise_384T_B1_float32.tflite"),
            BatchVariant(batchSize = 3, assetFileName = "realcugan3x_no_denoise_384T_B3_float32.tflite")
        )
    ),
    REALCUGAN_SCALE4X(
        displayName = "Real-CUGAN (4x)",
        descriptionRes = KMR.strings.desc_upscale_realcugan4x,
        scale = 4,
        tileContentSize = 384,
        offset = 152,  // = paddingPerSide(19) × 2 × scale(4)
        batchVariants = listOf(
            BatchVariant(batchSize = 1, assetFileName = "realcugan4x_no_denoise_384T_B1_float32.tflite")
        ),
    ),
    ;

    init {
        require(offset % (2 * scale) == 0) {
            "offset ($offset) deve essere divisibile per 2*scale ($scale) in $name"
        }
        require(batchVariants.isNotEmpty()) { "$name deve avere almeno una variante batch" }
        require(batchVariants.map { it.batchSize }.distinct().size == batchVariants.size) {
            "$name ha batch size duplicati tra le varianti"
        }
    }

    /** Pixel di padding da aggiungere per lato quando si estrae il tile dalla pagina sorgente. */
    val paddingPerSide: Int get() = offset / (2 * scale)

    /** Dimensione reale del tile da dare in input al modello (contenuto + padding). */
    val paddedTileSize: Int get() = tileContentSize + 2 * paddingPerSide

    /** Dimensione dell'output prodotto dal modello per un singolo tile. */
    val outSize: Int get() = scale * tileContentSize

    /** Valori di batch selezionabili in UI per questo modello, ordinati. */
    val availableBatchSizes: List<Int> get() = batchVariants.map { it.batchSize }.sorted()

    /** Variante esatta se esiste, altrimenti la più vicina disponibile (mai un crash su un valore stale in preferenze). */
    fun variantFor(requestedBatchSize: Int): BatchVariant =
        batchVariants.find { it.batchSize == requestedBatchSize }
            ?: batchVariants.minBy { kotlin.math.abs(it.batchSize - requestedBatchSize) }
}
