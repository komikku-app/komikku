package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable

@Serializable
class HentaiCafeSearchMetadata : RaisedSearchMetadata() {
    var hcId: String? = null
    var readerId: String? = null

    var url get() = hcId?.let { "$BASE_URL/$it" }
        set(a) {
            a?.let {
                hcId = hcIdFromUrl(a)
            }
        }

    var thumbnailUrl: String? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var artist: String? = null

    override fun copyTo(manga: SManga) {
        thumbnailUrl?.let { manga.thumbnail_url = it }

        manga.title = title!!
        manga.artist = artist
        manga.author = artist

        // Not available
        manga.status = SManga.UNKNOWN

        manga.genre = tagsToGenreString()

        /* val detailsDesc = "Title: $title\n" +
            "Artist: $artist\n"

        val tagsDesc = tagsToDescription()*/

        manga.description = "meta" /*listOf(detailsDesc, tagsDesc.toString())
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")*/
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        hcId?.let { pairs += Pair(context.getString(R.string.id), it) }
        readerId?.let { pairs += Pair(context.getString(R.string.reader_id), it) }
        thumbnailUrl?.let { pairs += Pair(context.getString(R.string.thumbnail_url), it) }
        title?.let { pairs += Pair(context.getString(R.string.title), it) }
        artist?.let { pairs += Pair(context.getString(R.string.artist), it) }
        return pairs
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        const val BASE_URL = "https://hentai.cafe"

        fun hcIdFromUrl(url: String) =
            url.split("/").last { it.isNotBlank() }
    }
}
