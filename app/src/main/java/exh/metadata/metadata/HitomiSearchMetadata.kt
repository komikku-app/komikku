package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import exh.metadata.MetadataUtil
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.util.nullIfEmpty
import kotlinx.serialization.Serializable
import tachiyomi.source.model.MangaInfo
import java.util.Date

@Serializable
class HitomiSearchMetadata : RaisedSearchMetadata() {
    var url get() = hlId?.let { urlFromHlId(it) }
        set(a) {
            a?.let {
                hlId = hlIdFromUrl(a)
            }
        }

    var hlId: String? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var thumbnailUrl: String? = null

    var artists: List<String> = emptyList()

    var genre: String? = null

    var language: String? = null

    var uploadDate: Long? = null

    override fun createMangaInfo(manga: MangaInfo): MangaInfo {
        val cover = thumbnailUrl

        val title = title

        // Copy tags -> genres
        val genres = tagsToGenreList()

        val artist = artists.joinToString()

        val status = MangaInfo.UNKNOWN

        val description = "meta"

        return manga.copy(
            cover = cover ?: manga.cover,
            title = title ?: manga.title,
            genres = genres,
            artist = artist,
            status = status,
            description = description,
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                getItem(hlId) { getString(R.string.id) },
                getItem(title) { getString(R.string.title) },
                getItem(thumbnailUrl) { getString(R.string.thumbnail_url) },
                getItem(artists.nullIfEmpty(), { it.joinToString() }) { getString(R.string.artist) },
                getItem(genre) { getString(R.string.genre) },
                getItem(language) { getString(R.string.language) },
                getItem(uploadDate, { MetadataUtil.EX_DATE_FORMAT.format(Date(it)) }) { getString(R.string.date_posted) },
            )
        }
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        const val BASE_URL = "https://hitomi.la"

        fun hlIdFromUrl(url: String) =
            url.split('/').last().split('-').last().substringBeforeLast('.')

        fun urlFromHlId(id: String) =
            "$BASE_URL/galleries/$id.html"
    }
}
