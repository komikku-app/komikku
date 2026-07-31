package eu.kanade.tachiyomi.util.upscale

import android.app.Application
import android.util.Log
import java.io.File

object BundledModelInstaller {

    /**
     * Copia una tantum, da assets/ a filesDir/models/, del modello di default
     * spedito dentro l'APK. Va chiamata all'avvio dell'app (es. dall'Application
     * class, o lazy al primo accesso in AiUpscaleCache) PRIMA di qualsiasi
     * controllo ModelDownloadManager.isDownloaded(), altrimenti il modello
     * bundled risulta sempre "non scaricato" e l'app tenta di riscaricarlo
     * inutilmente da GitHub anche se è già dentro l'APK.
     */
    fun ensureInstalled(context: Application, model: UpscaleModel, batchSize: Int) {
        val entry = ModelManifestLoader.entryFor(context, model, batchSize) ?: run {
            Log.w("BundledModelInstaller", "Nessuna entry manifest per ${model.name} B$batchSize, salto copia")
            return
        }

        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val destFile = File(modelsDir, entry.assetFileName)
        if (destFile.exists()) return // già installato, niente da fare

        val assetPath = "bundled_models/${entry.assetFileName}"
        try {
            context.assets.open(assetPath).use { input ->
                val tmpFile = File(modelsDir, "${entry.assetFileName}.part")
                tmpFile.outputStream().use { output -> input.copyTo(output) }
                tmpFile.renameTo(destFile)
            }
            Log.d("BundledModelInstaller", "Modello bundled ${entry.assetFileName} installato in filesDir/models/")
        } catch (e: java.io.FileNotFoundException) {
            // Il modello di default non è (più) bundled in APK, es. dopo un refactor
            // che l'ha spostato solo su download remoto: non è un errore, va semplicemente scaricato.
            Log.d("BundledModelInstaller", "${entry.assetFileName} non è bundled, verrà scaricato al bisogno")
        }
    }
}
