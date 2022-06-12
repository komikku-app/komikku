package exh.metadata.metadata

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTitle
import exh.util.nullIfEmpty
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
            description = description,
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                getItem(pvId) { getString(R.string.id) },
                getItem(url) { getString(R.string.url) },
                getItem(thumbnailUrl) { getString(R.string.thumbnail_url) },
                getItem(title) { getString(R.string.title) },
                getItem(altTitles.nullIfEmpty(), { it.joinToString() }) { getString(R.string.alt_titles) },
                getItem(artist) { getString(R.string.artist) },
                getItem(genre) { getString(R.string.genre) },
                getItem(rating) { getString(R.string.average_rating) },
                getItem(status) { getString(R.string.status) },
                getItem(lang) { getString(R.string.language) },
            )
        }
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0
        private const val TITLE_TYPE_ALT = 1

        const val TAG_TYPE_DEFAULT = 0

        private fun splitGalleryUrl(url: String) =
            url.toUri().pathSegments.filterNot(String::isNullOrBlank)

        fun pvIdFromUrl(url: String): String = splitGalleryUrl(url).last()
    }
}
