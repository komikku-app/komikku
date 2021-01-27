package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.MetadataUtil
import exh.metadata.metadata.base.RaisedSearchMetadata
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
        val pairs = mutableListOf<Pair<String, String>>()
        with(context) {
            hlId?.let { pairs += getString(R.string.id) to it }
            title?.let { pairs += getString(R.string.title) to it }
            thumbnailUrl?.let { pairs += getString(R.string.thumbnail_url) to it }
            val artists = artists.joinToString()
            if (artists.isNotBlank()) {
                pairs += getString(R.string.artist) to artists
            }
            group?.let { pairs += getString(R.string.group) to it }
            genre?.let { pairs += getString(R.string.genre) to it }
            language?.let { pairs += getString(R.string.language) to it }
            val series = series.joinToString()
            if (series.isNotBlank()) {
                pairs += getString(R.string.series) to series
            }
            val characters = characters.joinToString()
            if (characters.isNotBlank()) {
                pairs += getString(R.string.characters) to characters
            }
            uploadDate?.let { pairs += getString(R.string.date_posted) to MetadataUtil.EX_DATE_FORMAT.format(Date(it)) }
        }
        return pairs
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
