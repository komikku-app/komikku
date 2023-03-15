package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Response
import rx.Observable
import tachiyomi.core.preference.Preference

interface DataSaver {

    fun compress(imageUrl: String): String

    companion object {
        val NoOp = object : DataSaver {
            override fun compress(imageUrl: String): String {
                return imageUrl
            }
        }

        fun HttpSource.fetchImage(page: Page, dataSaver: DataSaver): Observable<Response> {
            val imageUrl = page.imageUrl ?: return fetchImage(page)
            page.imageUrl = dataSaver.compress(imageUrl)
            return fetchImage(page)
                .doOnNext {
                    page.imageUrl = imageUrl
                }
        }

        suspend fun HttpSource.getImage(page: Page, dataSaver: DataSaver): Response {
            val imageUrl = page.imageUrl ?: return getImage(page)
            page.imageUrl = dataSaver.compress(imageUrl)
            return try {
                getImage(page)
            } finally {
                page.imageUrl = imageUrl
            }
        }
    }
}

fun DataSaver(source: Source, preferences: SourcePreferences): DataSaver {
    return if (preferences.dataSaver().get() != 0 && source.id.toString() !in preferences.dataSaverExcludedSources().get()) {
        return DataSaverImpl(preferences)
    } else {
        DataSaver.NoOp
    }
}

private class DataSaverImpl(preferences: SourcePreferences) : DataSaver {
    private val dataSaver = preferences.dataSaver().get()
    private val dataSavedServer = preferences.dataSaverServer().get().trimEnd('/')

    private val ignoreJpg = preferences.dataSaverIgnoreJpeg().get()
    private val ignoreGif = preferences.dataSaverIgnoreGif().get()

    private val format = preferences.dataSaverImageFormatJpeg().toIntRepresentation()
    private val quality = preferences.dataSaverImageQuality().get()
    private val colorBW = preferences.dataSaverColorBW().toIntRepresentation()

    override fun compress(imageUrl: String): String {
        return if (dataSaver == 2 || (dataSaver == 1 && dataSavedServer.isNotBlank() && !imageUrl.contains(dataSavedServer))) {
            when {
                imageUrl.contains(".jpeg", true) || imageUrl.contains(".jpg", true) -> if (ignoreJpg) imageUrl else getUrl(imageUrl)
                imageUrl.contains(".gif", true) -> if (ignoreGif) imageUrl else getUrl(imageUrl)
                else -> getUrl(imageUrl)
            }
        } else {
            imageUrl
        }
    }

    private fun getUrl(imageUrl: String): String {
        if (dataSaver == 1) {
            // Network Request sent for the Bandwidth Hero Proxy server
            return "$dataSavedServer/?jpg=$format&l=$quality&bw=$colorBW&url=$imageUrl"
        } else {
            // Network Request sent to wsrv
            if (imageUrl.contains(".webp", true) || imageUrl.contains(".gif", true)) {
                if (format.toInt() == 0) {
                    // Preserve output image extension for animated images(.webp and .gif)
                    return "https://wsrv.nl/?url=$imageUrl&q=$quality&n=-1"
                } else {
                    // Do not preserve output Extension if User asked to convert into Jpeg
                    return "https://wsrv.nl/?url=$imageUrl&output=jpg&q=$quality&n=-1"
                }
            } else {
                if (format.toInt() == 1) {
                    return "https://wsrv.nl/?url=$imageUrl&output=jpg&q=$quality"
                } else {
                    return "https://wsrv.nl/?url=$imageUrl&output=webp&q=$quality"
                }
            }
        }
    }

    private fun Preference<Boolean>.toIntRepresentation() = if (get()) "1" else "0"
}
