@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import java.io.Serializable

interface SManga : Serializable {

    var url: String

    var title: String

    var thumbnail_url: String?

    var artist: String?

    var author: String?

    var status: Int

    var description: String?

    var genre: String?

    var update_strategy: UpdateStrategy

    var initialized: Boolean

    /**
     * Extra metadata associated with the manga.
     *
     * The JSON object is not visible to users and intended for internal or source-specific
     * purposes. Apps may define their own namespaced keys (e.g., `"mihon.*"`) for sources to populate.
     *
     * This allows apps to attach and ask for custom information without affecting the visible
     * manga data.
     *
     * @since tachiyomix 1.6
     */
    var memo: JsonObject

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    // SY -->
    val originalTitle: String
    val originalAuthor: String?
    val originalArtist: String?
    val originalThumbnailUrl: String?
    val originalDescription: String?
    val originalGenre: String?
    val originalStatus: Int
    // SY <--

    fun copy() = create().also {
        it.url = url
        // SY -->
        it.title = originalTitle
        it.artist = originalArtist
        it.author = originalAuthor
        it.thumbnail_url = originalThumbnailUrl
        it.description = originalDescription
        it.genre = originalGenre
        it.status = originalStatus
        // SY <--
        it.update_strategy = update_strategy
        it.initialized = initialized
        it.memo = memo
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

        // SY -->
        operator fun invoke(
            url: String,
            title: String,
            artist: String? = null,
            author: String? = null,
            description: String? = null,
            genre: String? = null,
            status: Int = 0,
            thumbnail_url: String? = null,
            initialized: Boolean = false,
        ): SManga {
            return create().also {
                it.url = url
                it.title = title
                it.artist = artist
                it.author = author
                it.description = description
                it.genre = genre
                it.status = status
                it.thumbnail_url = thumbnail_url
                it.initialized = initialized
            }
        }
        // SY <--
    }
}

// SY -->
fun SManga.copy(
    url: String = this.url,
    title: String = this.originalTitle,
    artist: String? = this.originalArtist,
    author: String? = this.originalAuthor,
    description: String? = this.originalDescription,
    genre: String? = this.originalGenre,
    status: Int = this.status,
    thumbnail_url: String? = this.originalThumbnailUrl,
    initialized: Boolean = this.initialized,
    // KMK -->
    memo: JsonObject = this.memo,
    // KMK <--
) = SManga.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre
    it.status = status
    it.thumbnail_url = thumbnail_url
    it.initialized = initialized
    // KMK -->
    it.memo = memo
    // KMK <--
}
// SY <--
