package exh.util

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DataSaver {
    private val preferences: PreferencesHelper = Injekt.get()

    fun compress(imageUrl: String): String {
        return if (preferences.dataSaver().get() && preferences.dataSaverServer().get().isNotBlank() && !imageUrl.contains(preferences.dataSaverServer().get() + "/?")) {
            when {
                imageUrl.contains(".jpeg", true) || imageUrl.contains(".jpg", true) -> if (preferences.ignoreJpeg().get()) imageUrl else getUrl(imageUrl)
                imageUrl.contains(".gif", true) -> if (preferences.ignoreGif().get()) imageUrl else getUrl(imageUrl)
                else -> getUrl(imageUrl)
            }
        } else imageUrl
    }

    private fun getUrl(imageUrl: String): String {
        val server = preferences.dataSaverServer().get() + "/?"
        val format = "jpg=${if (preferences.dataSaverImageFormatJpeg().get()) "1" else "0"}"
        val quality = "&l=${preferences.dataSaverImageQuality().get()}"
        val colorBW = "&bw=${if (preferences.dataSaverColorBW().get()) "1" else "0"}"
        val url = "$server$format$quality$colorBW&url="

        return url + imageUrl
    }
}
