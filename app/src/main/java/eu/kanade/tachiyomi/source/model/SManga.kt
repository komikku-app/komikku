package eu.kanade.tachiyomi.source.model

import data.Mangas
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable

interface SManga : Serializable {

    var url: String

    var title: String

    var artist: String?

    var author: String?

    var description: String?

    var genre: String?

    var status: Int

    var thumbnail_url: String?

    var initialized: Boolean

    // SY -->
    val originalTitle: String
        get() = (this as? MangaImpl)?.ogTitle ?: title
    val originalAuthor: String?
        get() = (this as? MangaImpl)?.ogAuthor ?: author
    val originalArtist: String?
        get() = (this as? MangaImpl)?.ogArtist ?: artist
    val originalDescription: String?
        get() = (this as? MangaImpl)?.ogDesc ?: description
    val originalGenre: String?
        get() = (this as? MangaImpl)?.ogGenre ?: genre
    val originalStatus: Int
        get() = (this as? MangaImpl)?.ogStatus ?: status
    // SY <--

    fun copyFrom(other: SManga) {
        // EXH -->
        if (other.title.isNotBlank() && originalTitle != other.title) {
            val oldTitle = originalTitle
            title = other.originalTitle
            val source = (this as? Manga)?.source
            if (source != null) {
                Injekt.get<DownloadManager>().renameMangaDir(oldTitle, other.originalTitle, source)
            }
        }
        // EXH <--

        if (other.author != null) {
            author = /* SY --> */ other.originalAuthor // SY <--
        }

        if (other.artist != null) {
            artist = /* SY --> */ other.originalArtist // SY <--
        }

        if (other.description != null) {
            description = /* SY --> */ other.originalDescription // SY <--
        }

        if (other.genre != null) {
            genre = /* SY --> */ other.originalGenre // SY <--
        }

        if (other.thumbnail_url != null) {
            thumbnail_url = other.thumbnail_url
        }

        status = other.status

        if (!initialized) {
            initialized = other.initialized
        }
    }

    fun copyFrom(other: Mangas) {
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
            genre = other.genre.joinToString(separator = ", ")
        }

        if (other.thumbnail_url != null) {
            thumbnail_url = other.thumbnail_url
        }

        status = other.status.toInt()

        if (!initialized) {
            initialized = other.initialized
        }
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SManga {
            return SMangaImpl()
        }
    }
}

fun SManga.toMangaInfo(): MangaInfo {
    return MangaInfo(
        key = this.url,
        title = this.title,
        artist = this.artist ?: "",
        author = this.author ?: "",
        description = this.description ?: "",
        genres = this.genre?.split(", ") ?: emptyList(),
        status = this.status,
        cover = this.thumbnail_url ?: "",
    )
}

fun MangaInfo.toSManga(): SManga {
    val mangaInfo = this
    return SManga.create().apply {
        url = mangaInfo.key
        title = mangaInfo.title
        artist = mangaInfo.artist
        author = mangaInfo.author
        description = mangaInfo.description
        genre = mangaInfo.genres.joinToString(", ")
        status = mangaInfo.status
        thumbnail_url = mangaInfo.cover
    }
}
