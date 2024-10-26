@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

class SMangaImpl : SManga {

    override lateinit var url: String

    // SY -->
    override var title: String = ""
    // SY <--

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    override var initialized: Boolean = false

    // SY -->
    override val originalTitle: String
        get() = title
    override val originalAuthor: String?
        get() = author
    override val originalArtist: String?
        get() = artist
    override val originalThumbnailUrl: String?
        get() = thumbnail_url
    override val originalDescription: String?
        get() = description
    override val originalGenre: String?
        get() = genre
    override val originalStatus: Int
        get() = status
    // SY <--
}
