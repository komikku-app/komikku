package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.copy
import exh.metadata.MetadataUtil
import kotlinx.serialization.Serializable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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

    override fun createMangaInfo(manga: SManga): SManga {
        val key = nhId?.let { nhIdToPath(it) }

        val cover = if (mediaId != null) {
            typeToExtension(coverImageType)?.let {
                "https://t.nhentai.net/galleries/$mediaId/cover.$it"
            }
        } else {
            null
        }

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
        val genres = tagsToGenreString()

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

        val description = null

        return manga.copy(
            url = key ?: manga.url,
            thumbnail_url = cover ?: manga.thumbnail_url,
            title = title,
            artist = artist ?: manga.artist,
            genre = genres,
            status = status,
            description = description,
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                getItem(nhId) { stringResource(SYMR.strings.id) },
                getItem(
                    uploadDate,
                    {
                        MetadataUtil.EX_DATE_FORMAT
                            .format(ZonedDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()))
                    },
                ) {
                    stringResource(SYMR.strings.date_posted)
                },
                getItem(favoritesCount) { stringResource(SYMR.strings.total_favorites) },
                getItem(mediaId) { stringResource(SYMR.strings.media_id) },
                getItem(japaneseTitle) { stringResource(SYMR.strings.japanese_title) },
                getItem(englishTitle) { stringResource(SYMR.strings.english_title) },
                getItem(shortTitle) { stringResource(SYMR.strings.short_title) },
                getItem(coverImageType) { stringResource(SYMR.strings.cover_image_file_type) },
                getItem(pageImageTypes.size) { stringResource(SYMR.strings.page_count) },
                getItem(thumbnailImageType) { stringResource(SYMR.strings.thumbnail_image_file_type) },
                getItem(scanlator) { stringResource(MR.strings.scanlator) },
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
                "w" -> "webp"
                else -> null
            }

        fun nhUrlToId(url: String) =
            url.split("/").last { it.isNotBlank() }.toLong()

        fun nhIdToPath(id: Long) = "/g/$id/"
    }
}
