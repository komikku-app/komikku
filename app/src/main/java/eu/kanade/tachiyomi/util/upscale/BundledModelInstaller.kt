package eu.kanade.tachiyomi.util.upscale

import android.app.Application
import exh.log.xLogD
import exh.log.xLogW
import java.io.File

object BundledModelInstaller {
    @Volatile
    private var installed = false
    /**
     * One-time copy, from assets/ to filesDir/models/, of the default model
     * shipped inside the APK. Must be called when the app starts (e.g. lazy on first login in AiUpscaleCache) BEFORE any
     * ModelDownloadManager.isDownloaded() control, otherwise the model
     * bundled always says "not downloaded" and the app tries to redownload it
     * uselessly from GitHub even if it's already inside the APK.
     */
    fun ensureInstalled(context: Application, model: UpscaleModel, batchSize: Int) {
        if (installed) return

        val entry = ModelManifestLoader.entryFor(context, model, batchSize) ?: run {
            xLogW("No entry manifest for ${model.name} B$batchSize, avoid copy")
            return
        }

        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val destFile = File(modelsDir, entry.assetFileName)
        if (destFile.exists()) {
            installed = true
            return
        }

        val assetPath = "bundled_models/${entry.assetFileName}"
        try {
            context.assets.open(assetPath).use { input ->
                val tmpFile = File(modelsDir, "${entry.assetFileName}.part")
                tmpFile.outputStream().use { output -> input.copyTo(output) }
                tmpFile.renameTo(destFile)
            }
            installed = true
            xLogD("Bundled model ${entry.assetFileName} installed in filesDir/models/")
        } catch (_: java.io.FileNotFoundException) {
            installed = true
            xLogD("${entry.assetFileName} is not bundled, will be donwloaded as needed")
        }
    }
}
