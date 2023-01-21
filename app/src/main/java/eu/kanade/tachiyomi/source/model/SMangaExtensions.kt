package eu.kanade.tachiyomi.source.model

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.data.Mangas
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun SManga.copyFrom(other: Mangas) {
    // EXH -->
    if (other.title.isNotBlank() && originalTitle != other.title) {
        val oldTitle = originalTitle
        title = other.title
        val source = (this as? Manga)?.source
        if (source != null) {
            Injekt.get<DownloadManager>().renameMangaDir(oldTitle, other.title, source)
        }
    }
    // EXH <--

    if (other.author != null) {
        author = other.author
    }

    if (other.artist != null) {
        artist = other.artist
    }

    if (other.description != null) {
        description = other.description
    }

    if (other.genre != null) {
        genre = other.genre!!.joinToString(separator = ", ")
    }

    if (other.thumbnail_url != null) {
        thumbnail_url = other.thumbnail_url
    }

    status = other.status.toInt()

    if (!initialized) {
        initialized = other.initialized
    }
}
