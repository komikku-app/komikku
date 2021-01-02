package exh.metadata.metadata

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.MetadataUtil
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable
import tachiyomi.source.model.MangaInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
class TsuminoSearchMetadata : RaisedSearchMetadata() {
    var tmId: Int? = null

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var artist: String? = null

    var uploadDate: Long? = null

    var length: Int? = null

    var ratingString: String? = null

    var averageRating: Float? = null

    var userRatings: Long? = null

    var favorites: Long? = null

    var category: String? = null

    var collection: String? = null

    var group: String? = null

    var parody: List<String> = emptyList()

    var character: List<String> = emptyList()

    override fun createMangaInfo(manga: MangaInfo): MangaInfo {
        val title = title
        val cover = tmId?.let { BASE_URL.replace("www", "content") + thumbUrlFromId(it.toString()) }

        val artist = artist

        val status = SManga.UNKNOWN

        // Copy tags -> genres
        val genres = tagsToGenreList()

        val description = "meta"

        return manga.copy(
            title = title ?: manga.title,
            cover = cover ?: manga.cover,
            artist = artist ?: manga.artist,
            status = status,
            genres = genres,
            description = description
        )
    }

    override fun copyTo(manga: SManga) {
        title?.let { manga.title = it }
        manga.thumbnail_url = BASE_URL.replace("www", "content") + thumbUrlFromId(tmId.toString())

        artist?.let { manga.artist = it }

        manga.status = SManga.UNKNOWN

        // Copy tags -> genres
        manga.genre = tagsToGenreString()

        manga.description = "meta"
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        tmId?.let { pairs += context.getString(R.string.id) to it.toString() }
        title?.let { pairs += context.getString(R.string.title) to it }
        uploader?.let { pairs += context.getString(R.string.uploader) to it }
        uploadDate?.let { pairs += context.getString(R.string.date_posted) to MetadataUtil.EX_DATE_FORMAT.format(Date(it)) }
        length?.let { pairs += context.getString(R.string.page_count) to it.toString() }
        ratingString?.let { pairs += context.getString(R.string.rating_string) to it }
        averageRating?.let { pairs += context.getString(R.string.average_rating) to it.toString() }
        userRatings?.let { pairs += context.getString(R.string.total_ratings) to it.toString() }
        favorites?.let { pairs += context.getString(R.string.total_favorites) to it.toString() }
        category?.let { pairs += context.getString(R.string.genre) to it }
        collection?.let { pairs += context.getString(R.string.collection) to it }
        group?.let { pairs += context.getString(R.string.group) to it }
        val parodiesString = parody.joinToString()
        if (parodiesString.isNotEmpty()) {
            pairs += context.getString(R.string.parodies) to parodiesString
        }
        val charactersString = character.joinToString()
        if (charactersString.isNotEmpty()) {
            pairs += context.getString(R.string.characters) to charactersString
        }
        return pairs
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        val BASE_URL = "https://www.tsumino.com"

        val TSUMINO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun tmIdFromUrl(url: String) = url.toUri().lastPathSegment

        fun thumbUrlFromId(id: String) = "/thumbs/$id/1"
    }
}
