package eu.kanade.tachiyomi.util.upscale

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ModelVariantEntry(
    val modelId: String,
    val batchSize: Int,
    val assetFileName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

@Serializable
data class ModelManifest(
    val version: Int,
    val variants: List<ModelVariantEntry>,
)

object ModelManifestLoader {
    private val json = Json { ignoreUnknownKeys = true }

    private var cached: ModelManifest? = null

    fun load(context: Context): ModelManifest {
        cached?.let { return it }
        val text = context.assets.open("models_manifest.json").bufferedReader().use { it.readText() }
        return json.decodeFromString<ModelManifest>(text).also { cached = it }
    }

    /** Entry manifest per una precisa (modello, batch). Null se quella combinazione non è nel manifest. */
    fun entryFor(context: Context, model: UpscaleModel, batchSize: Int): ModelVariantEntry? =
        load(context).variants.find { it.modelId == model.name && it.batchSize == batchSize }
}
