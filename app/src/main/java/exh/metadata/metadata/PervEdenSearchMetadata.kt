package exh.metadata.metadata

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTitle
import kotlinx.serialization.Serializable
import tachiyomi.source.model.MangaInfo

@Serializable
class PervEdenSearchMetadata : RaisedSearchMetadata() {
    var pvId: String? = null

    var url: String? = null
    var thumbnailUrl: String? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)
    var altTitles
        get() = titles.filter { it.type == TITLE_TYPE_ALT }.map { it.title }
        set(value) {
            titles.removeAll { it.type == TITLE_TYPE_ALT }
            titles += value.map { RaisedTitle(it, TITLE_TYPE_ALT) }
        }

    var artist: String? = null

    var genre: String? = null

    var rating: Float? = null

    var status: String? = null

    var lang: String? = null

    override fun createMangaInfo(manga: MangaInfo): MangaInfo {
        val key = url
        val cover = thumbnailUrl

        val title = title

        val artist = artist

        val status = when (status) {
            "Ongoing" -> MangaInfo.ONGOING
            "Completed", "Suspended" -> MangaInfo.COMPLETED
            else -> MangaInfo.UNKNOWN
        }

        // Copy tags -> genres
        val genres = tagsToGenreList()

        val description = "meta"

        return manga.copy(
            key = key ?: manga.key,
            cover = cover ?: manga.cover,
            title = title ?: manga.title,
            artist = artist ?: manga.artist,
            status = status,
            genres = genres,
            description = description
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pvId?.let { pairs += context.getString(R.string.id) to it }
        url?.let { pairs += context.getString(R.string.url) to it }
        thumbnailUrl?.let { pairs += context.getString(R.string.thumbnail_url) to it }
        title?.let { pairs += context.getString(R.string.title) to it }
        val altTitles = altTitles.joinToString()
        if (altTitles.isNotBlank()) {
            pairs += context.getString(R.string.alt_titles) to altTitles
        }
        artist?.let { pairs += context.getString(R.string.artist) to it }
        genre?.let { pairs += context.getString(R.string.genre) to it }
        rating?.let { pairs += context.getString(R.string.average_rating) to it.toString() }
        status?.let { pairs += context.getString(R.string.status) to it }
        lang?.let { pairs += context.getString(R.string.language) to it }
        return pairs
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0
        private const val TITLE_TYPE_ALT = 1

        const val TAG_TYPE_DEFAULT = 0

        private fun splitGalleryUrl(url: String) =
            url.let {
                it.toUri().pathSegments.filterNot(String::isNullOrBlank)
            }

        fun pvIdFromUrl(url: String): String = splitGalleryUrl(url).last()
    }
}
