package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.EX_DATE_FORMAT
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
        hlId?.let { pairs += Pair(context.getString(R.string.id), it) }
        title?.let { pairs += Pair(context.getString(R.string.title), it) }
        thumbnailUrl?.let { pairs += Pair(context.getString(R.string.thumbnail_url), it) }
        val artists = artists.joinToString()
        if (artists.isNotBlank()) {
            pairs += Pair(context.getString(R.string.artist), artists)
        }
        group?.let { pairs += Pair(context.getString(R.string.group), it) }
        genre?.let { pairs += Pair(context.getString(R.string.genre), it) }
        language?.let { pairs += Pair(context.getString(R.string.language), it) }
        val series = series.joinToString()
        if (series.isNotBlank()) {
            pairs += Pair(context.getString(R.string.series), series)
        }
        val characters = characters.joinToString()
        if (characters.isNotBlank()) {
            pairs += Pair(context.getString(R.string.characters), characters)
        }
        uploadDate?.let { pairs += Pair(context.getString(R.string.date_posted), EX_DATE_FORMAT.format(Date(it))) }
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
