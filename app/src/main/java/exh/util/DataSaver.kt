package exh.util

import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy

object DataSaver {
    private val preferences: PreferencesHelper by injectLazy()

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
        val format = "jpg=" + preferences.dataSaverImageFormatJpeg().toIntRepresentation()
        val quality = "l=" + preferences.dataSaverImageQuality().get()
        val colorBW = "bw=" + preferences.dataSaverColorBW().toIntRepresentation()
        val url = "url=$imageUrl"

        return "$server&$format&$quality&$colorBW&$url"
    }

    private fun Preference<Boolean>.toIntRepresentation() = if (get()) "1" else "0"
}
