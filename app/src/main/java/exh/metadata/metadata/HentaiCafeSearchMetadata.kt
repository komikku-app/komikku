package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable
import tachiyomi.source.model.MangaInfo

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

    override fun createMangaInfo(manga: MangaInfo): MangaInfo {
        val cover = thumbnailUrl

        val title = title
        val artist = artist
        val author = artist

        // Not available
        val status = MangaInfo.UNKNOWN

        val genres = tagsToGenreList()

        val description = "meta"

        return manga.copy(
            cover = cover ?: manga.cover,
            title = title ?: manga.title,
            artist = artist ?: manga.artist,
            author = author ?: manga.author,
            status = status,
            genres = genres,
            description = description
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        hcId?.let { pairs += context.getString(R.string.id) to it }
        readerId?.let { pairs += context.getString(R.string.reader_id) to it }
        thumbnailUrl?.let { pairs += context.getString(R.string.thumbnail_url) to it }
        title?.let { pairs += context.getString(R.string.title) to it }
        artist?.let { pairs += context.getString(R.string.artist) to it }
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
