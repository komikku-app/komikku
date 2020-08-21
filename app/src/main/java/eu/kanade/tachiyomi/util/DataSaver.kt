package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy

class DataSaver() {

    private val prefs: PreferencesHelper by injectLazy()

    fun compress(imageUrl: String): String {
        val server = prefs.dataSaverServer().get() + "/?"
        val format = "jpeg=${if (prefs.dataSaverImageFormatJpeg().get()) "1" else "0"}"
        val quality = "&l=${prefs.dataSaverImageQuality().get()}"
        val colorBW = "&bw=${if (prefs.dataSaverColorBW().get()) "1" else "0"}"
        val url = "$server$format$quality$colorBW&url="
        val ignoreJpeg: Boolean = prefs.ignoreJpeg().get()
        val ignoreGif: Boolean = prefs.ignoreGif().get()
        val dataSaverStatus: Boolean = prefs.dataSaver().get()
        var process = false
        var processedUrl = imageUrl

        if (dataSaverStatus) process = true
        if (imageUrl.contains(server)) process = false
        if (ignoreJpeg) {
            if (imageUrl.contains(".jpeg", true) || imageUrl.contains(".jpg", true)) process = false
        }
        if (ignoreGif) {
            if (imageUrl.contains(".gif", true)) process = false
        }
        if (process) processedUrl = url + imageUrl

        return processedUrl
    }
}
