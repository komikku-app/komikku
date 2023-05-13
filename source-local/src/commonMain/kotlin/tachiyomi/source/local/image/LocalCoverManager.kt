package tachiyomi.source.local.image

import eu.kanade.tachiyomi.source.model.SManga
import java.io.File
import java.io.InputStream

expect class LocalCoverManager {

    fun find(mangaUrl: String): File?

    // SY -->
    fun update(manga: SManga, inputStream: InputStream, encrypted: Boolean = false): File?
    // SY <--
}
