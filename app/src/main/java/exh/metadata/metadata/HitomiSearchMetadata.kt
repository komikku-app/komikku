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

    var group: String? = null

    var genre: String? = null

    var language: String? = null

    var series: List<String> = emptyList()

    var characters: List<String> = emptyList()

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
            description = description
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                hlId?.let { getString(R.string.id) to it },
                title?.let { getString(R.string.title) to it },
                thumbnailUrl?.let { getString(R.string.thumbnail_url) to it },
                artists.nullIfEmpty()?.joinToString()?.let { getString(R.string.artist) to it },
                group?.let { getString(R.string.group) to it },
                genre?.let { getString(R.string.genre) to it },
                language?.let { getString(R.string.language) to it },
                series.nullIfEmpty()?.joinToString()?.let { getString(R.string.series) to it },
                characters.nullIfEmpty()?.joinToString()?.let { getString(R.string.characters) to it },
                uploadDate?.let { getString(R.string.date_posted) to MetadataUtil.EX_DATE_FORMAT.format(Date(it)) }
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
