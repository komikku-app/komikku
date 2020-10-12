package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.EX_DATE_FORMAT
import exh.metadata.ONGOING_SUFFIX
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable
import uy.kohesive.injekt.api.get
import java.util.Date

@Serializable
class NHentaiSearchMetadata : RaisedSearchMetadata() {
    var url get() = nhId?.let { BASE_URL + nhIdToPath(it) }
        set(a) {
            a?.let {
                nhId = nhUrlToId(a)
            }
        }

    var nhId: Long? = null

    var uploadDate: Long? = null

    var favoritesCount: Long? = null

    var mediaId: String? = null

    var japaneseTitle by titleDelegate(TITLE_TYPE_JAPANESE)
    var englishTitle by titleDelegate(TITLE_TYPE_ENGLISH)
    var shortTitle by titleDelegate(TITLE_TYPE_SHORT)

    var coverImageType: String? = null
    var pageImageTypes: List<String> = emptyList()
    var thumbnailImageType: String? = null

    var scanlator: String? = null

    override fun copyTo(manga: SManga) {
        nhId?.let { manga.url = nhIdToPath(it) }

        if (mediaId != null) {
            typeToExtension(coverImageType)?.let {
                manga.thumbnail_url = "https://t.nhentai.net/galleries/$mediaId/cover.$it"
            }
        }

        manga.title = englishTitle ?: japaneseTitle ?: shortTitle!!

        // Set artist (if we can find one)
        tags.filter { it.namespace == NHENTAI_ARTIST_NAMESPACE }.let {
            if (it.isNotEmpty()) manga.artist = it.joinToString(transform = { it.name })
        }

        // Copy tags -> genres
        manga.genre = tagsToGenreString()

        // Try to automatically identify if it is ongoing, we try not to be too lenient here to avoid making mistakes
        // We default to completed
        manga.status = SManga.COMPLETED
        englishTitle?.let { t ->
            ONGOING_SUFFIX.find {
                t.endsWith(it, ignoreCase = true)
            }?.let {
                manga.status = SManga.ONGOING
            }
        }

        manga.description = "meta"
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        nhId?.let { pairs += Pair(context.getString(R.string.id), it.toString()) }
        uploadDate?.let { pairs += Pair(context.getString(R.string.date_posted), EX_DATE_FORMAT.format(Date(it * 1000))) }
        favoritesCount?.let { pairs += Pair(context.getString(R.string.total_favorites), it.toString()) }
        mediaId?.let { pairs += Pair(context.getString(R.string.media_id), it) }
        japaneseTitle?.let { pairs += Pair(context.getString(R.string.japanese_title), it) }
        englishTitle?.let { pairs += Pair(context.getString(R.string.english_title), it) }
        shortTitle?.let { pairs += Pair(context.getString(R.string.short_title), it) }
        coverImageType?.let { pairs += Pair(context.getString(R.string.cover_image_file_type), it) }
        pageImageTypes.size.let { pairs += Pair(context.getString(R.string.page_count), it.toString()) }
        thumbnailImageType?.let { pairs += Pair(context.getString(R.string.thumbnail_image_file_type), it) }
        scanlator?.let { pairs += Pair(context.getString(R.string.scanlator), it) }
        return pairs
    }

    companion object {
        private const val TITLE_TYPE_JAPANESE = 0
        private const val TITLE_TYPE_ENGLISH = 1
        private const val TITLE_TYPE_SHORT = 2

        const val TAG_TYPE_DEFAULT = 0

        const val BASE_URL = "https://nhentai.net"

        private const val NHENTAI_ARTIST_NAMESPACE = "artist"
        const val NHENTAI_CATEGORIES_NAMESPACE = "category"

        fun typeToExtension(t: String?) =
            when (t) {
                "p" -> "png"
                "j" -> "jpg"
                "g" -> "gif"
                else -> null
            }

        fun nhUrlToId(url: String) =
            url.split("/").last { it.isNotBlank() }.toLong()

        fun nhIdToPath(id: Long) = "/g/$id/"
    }
}
