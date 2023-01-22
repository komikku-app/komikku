package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

open class MangaImpl : Manga {

    override var id: Long? = null

    override var source: Long = -1

    override lateinit var url: String

    // SY -->
    private val customManga: CustomMangaInfo?
        get() = getCustomMangaInfo.get(id!!)

    override var title: String
        get() = if (favorite) {
            customManga?.title ?: ogTitle
        } else {
            ogTitle
        }
        set(value) {
            ogTitle = value
        }

    override var author: String?
        get() = if (favorite) customManga?.author ?: ogAuthor else ogAuthor
        set(value) { ogAuthor = value }

    override var artist: String?
        get() = if (favorite) customManga?.artist ?: ogArtist else ogArtist
        set(value) { ogArtist = value }

    override var description: String?
        get() = if (favorite) customManga?.description ?: ogDesc else ogDesc
        set(value) { ogDesc = value }

    override var genre: String?
        get() = if (favorite) customManga?.genre?.joinToString() ?: ogGenre else ogGenre
        set(value) { ogGenre = value }

    override var status: Int
        get() = if (favorite) customManga?.status?.toInt() ?: ogStatus else ogStatus
        set(value) { ogStatus = value }
    // SY <--

    override var thumbnail_url: String? = null

    override var favorite: Boolean = false

    override var last_update: Long = 0

    override var date_added: Long = 0

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    override var initialized: Boolean = false

    override var viewer_flags: Int = 0

    override var chapter_flags: Int = 0

    override var cover_last_modified: Long = 0

    override var filtered_scanlators: String? = null

    // SY -->
    lateinit var ogTitle: String
        private set
    var ogAuthor: String? = null
        private set
    var ogArtist: String? = null
        private set
    var ogDesc: String? = null
        private set
    var ogGenre: String? = null
        private set
    var ogStatus: Int = 0
        private set

    override val originalTitle: String
        get() = ogTitle
    override val originalAuthor: String?
        get() = ogAuthor ?: author
    override val originalArtist: String?
        get() = ogArtist ?: artist
    override val originalDescription: String?
        get() = ogDesc ?: description
    override val originalGenre: String?
        get() = ogGenre ?: genre
    override val originalStatus: Int
        get() = ogStatus
    // SY <--

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val manga = other as Manga
        if (url != manga.url) return false
        return id == manga.id
    }

    override fun hashCode(): Int {
        return url.hashCode() + id.hashCode()
    }

    // SY -->
    override fun copyFrom(other: SManga) {
        // EXH -->
        if (other.title.isNotBlank() && originalTitle != other.title) {
            val source = (this as? Manga)?.source
            if (source != null) {
                Injekt.get<DownloadManager>().renameMangaDir(originalTitle, other.originalTitle, source)
            }
        }
        // EXH <--
        super.copyFrom(other)
    }

    companion object {
        private val getCustomMangaInfo: GetCustomMangaInfo by injectLazy()
    }
    // SY <--
}
