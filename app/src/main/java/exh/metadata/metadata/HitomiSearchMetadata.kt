package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.MetadataUtil
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable
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

    override fun copyTo(manga: SManga) {
        thumbnailUrl?.let { manga.thumbnail_url = it }

        title?.let {
            manga.title = it
        }

        // Copy tags -> genres
        manga.genre = tagsToGenreString()

        manga.artist = artists.joinToString()

        manga.status = SManga.UNKNOWN

        /*val titleDesc = StringBuilder()

        title?.let {
            titleDesc += "Title: $it\n"
        }

        val detailsDesc = StringBuilder()

        detailsDesc += "Artist(s): ${manga.artist}\n"

        group?.let {
            detailsDesc += "Group: $it\n"
        }

        type?.let {
            detailsDesc += "Type: ${it.capitalize()}\n"
        }

        (language ?: "unknown").let {
            detailsDesc += "Language: ${it.capitalize()}\n"
        }

        if (series.isNotEmpty()) {
            detailsDesc += "Series: ${series.joinToString()}\n"
        }

        if (characters.isNotEmpty()) {
            detailsDesc += "Characters: ${characters.joinToString()}\n"
        }

        uploadDate?.let {
            detailsDesc += "Upload date: ${EX_DATE_FORMAT.format(Date(it))}\n"
        }

        val tagsDesc = tagsToDescription()*/

        manga.description = "meta" /*listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")*/
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
