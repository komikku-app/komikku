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

    var preferredTitle: Int? = null

    override fun createMangaInfo(manga: MangaInfo): MangaInfo {
        val key = nhId?.let { nhIdToPath(it) }

        val cover = if (mediaId != null) {
            typeToExtension(coverImageType)?.let {
                "https://t.nhentai.net/galleries/$mediaId/cover.$it"
            }
        } else null

        val title = when (preferredTitle) {
            TITLE_TYPE_SHORT -> shortTitle ?: englishTitle ?: japaneseTitle ?: manga.title
            0, TITLE_TYPE_ENGLISH -> englishTitle ?: japaneseTitle ?: shortTitle ?: manga.title
            else -> englishTitle ?: japaneseTitle ?: shortTitle ?: manga.title
        }

        // Set artist (if we can find one)
        val artist = tags.ofNamespace(NHENTAI_ARTIST_NAMESPACE).let { tags ->
            if (tags.isNotEmpty()) tags.joinToString(transform = { it.name }) else null
        }

        // Copy tags -> genres
        val genres = tagsToGenreList()

        // Try to automatically identify if it is ongoing, we try not to be too lenient here to avoid making mistakes
        // We default to completed
        var status = SManga.COMPLETED
        englishTitle?.let { t ->
            MetadataUtil.ONGOING_SUFFIX.find {
                t.endsWith(it, ignoreCase = true)
            }?.let {
                status = SManga.ONGOING
            }
        }

        val description = "meta"

        return manga.copy(
            key = key ?: manga.key,
            cover = cover ?: manga.cover,
            title = title,
            artist = artist ?: manga.artist,
            genres = genres,
            status = status,
            description = description,
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                getItem(nhId) { getString(R.string.id) },
                getItem(uploadDate, { MetadataUtil.EX_DATE_FORMAT.format(Date(it * 1000)) }) { getString(R.string.date_posted) },
                getItem(favoritesCount) { getString(R.string.total_favorites) },
                getItem(mediaId) { getString(R.string.media_id) },
                getItem(japaneseTitle) { getString(R.string.japanese_title) },
                getItem(englishTitle) { getString(R.string.english_title) },
                getItem(shortTitle) { getString(R.string.short_title) },
                getItem(coverImageType) { getString(R.string.cover_image_file_type) },
                getItem(pageImageTypes.size) { getString(R.string.page_count) },
                getItem(thumbnailImageType) { getString(R.string.thumbnail_image_file_type) },
                getItem(scanlator) { getString(R.string.scanlator) },
            )
        }
    }

    companion object {
        private const val TITLE_TYPE_JAPANESE = 0
        const val TITLE_TYPE_ENGLISH = 1
        const val TITLE_TYPE_SHORT = 2

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
