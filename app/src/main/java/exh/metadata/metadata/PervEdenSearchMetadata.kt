package exh.metadata.metadata

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTitle
import kotlinx.serialization.Serializable

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

    override fun copyTo(manga: SManga) {
        url?.let { manga.url = it }
        thumbnailUrl?.let { manga.thumbnail_url = it }

        title?.let {
            manga.title = it
        }

        artist?.let {
            manga.artist = it
        }

        status?.let {
            manga.status = when (it) {
                "Ongoing" -> SManga.ONGOING
                "Completed", "Suspended" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }

        // Copy tags -> genres
        manga.genre = tagsToGenreString()

        /*val titleDesc = StringBuilder()

        title?.let {
            titleDesc += "Title: $it\n"
        }
        if (altTitles.isNotEmpty()) {
            titleDesc += "Alternate Titles: \n" + altTitles
                .joinToString(separator = "\n", postfix = "\n") {
                    "▪ $it"
                }
        }

        val detailsDesc = StringBuilder()
        artist?.let {
            detailsDesc += "Artist: $it\n"
        }

        type?.let {
            detailsDesc += "Type: $it\n"
        }

        status?.let {
            detailsDesc += "Status: $it\n"
        }

        rating?.let {
            detailsDesc += "Rating: %.2\n".format(it)
        }


        val tagsDesc = tagsToDescription()*/

        manga.description = "meta" /*listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")*/
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pvId?.let { pairs += Pair(context.getString(R.string.id), it) }
        url?.let { pairs += Pair(context.getString(R.string.url), it) }
        thumbnailUrl?.let { pairs += Pair(context.getString(R.string.thumbnail_url), it) }
        title?.let { pairs += Pair(context.getString(R.string.title), it) }
        val altTitles = altTitles.joinToString()
        if (altTitles.isNotBlank()) {
            pairs += Pair(context.getString(R.string.alt_titles), altTitles)
        }
        artist?.let { pairs += Pair(context.getString(R.string.artist), it) }
        genre?.let { pairs += Pair(context.getString(R.string.genre), it) }
        rating?.let { pairs += Pair(context.getString(R.string.average_rating), it.toString()) }
        status?.let { pairs += Pair(context.getString(R.string.status), it) }
        lang?.let { pairs += Pair(context.getString(R.string.language), it) }
        return pairs
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0
        private const val TITLE_TYPE_ALT = 1

        const val TAG_TYPE_DEFAULT = 0

        private fun splitGalleryUrl(url: String) =
            url.let {
                Uri.parse(it).pathSegments.filterNot(String::isNullOrBlank)
            }

        fun pvIdFromUrl(url: String): String = splitGalleryUrl(url).last()
    }
}
