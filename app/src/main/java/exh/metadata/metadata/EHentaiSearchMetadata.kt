package exh.metadata.metadata

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import exh.metadata.MetadataUtil
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

@Serializable
class EHentaiSearchMetadata : RaisedSearchMetadata() {
    var gId: String?
        get() = indexedExtra
        set(value) { indexedExtra = value }

    var gToken: String? = null
    var exh: Boolean? = null
    var thumbnailUrl: String? = null

    var title by titleDelegate(TITLE_TYPE_TITLE)
    var altTitle by titleDelegate(TITLE_TYPE_ALT_TITLE)

    var genre: String? = null

    var datePosted: Long? = null
    var parent: String? = null

    var visible: String? = null // Not a boolean
    var language: String? = null
    var translated: Boolean? = null
    var size: Long? = null
    var length: Int? = null
    var favorites: Int? = null
    var ratingCount: Int? = null
    var averageRating: Double? = null

    var aged: Boolean = false
    var lastUpdateCheck: Long = 0

    override fun createMangaInfo(manga: MangaInfo): MangaInfo {
        val key = gId?.let { gId ->
            gToken?.let { gToken ->
                idAndTokenToUrl(gId, gToken)
            }
        }
        val cover = thumbnailUrl

        // No title bug?
        val title = altTitle
            ?.takeIf { Injekt.get<PreferencesHelper>().useJapaneseTitle().get() }
            ?: title

        // Set artist (if we can find one)
        val artist = tags.ofNamespace(EH_ARTIST_NAMESPACE)
            .ifEmpty { null }
            ?.joinToString { it.name }

        // Copy tags -> genres
        val genres = tagsToGenreList()

        // Try to automatically identify if it is ongoing, we try not to be too lenient here to avoid making mistakes
        // We default to completed
        var status = MangaInfo.COMPLETED
        title?.let { t ->
            MetadataUtil.ONGOING_SUFFIX.find {
                t.endsWith(it, ignoreCase = true)
            }?.let {
                status = MangaInfo.ONGOING
            }
        }

        val description = "meta"

        return manga.copy(
            key = key ?: manga.key,
            title = title ?: manga.title,
            artist = artist ?: manga.artist,
            description = description,
            genres = genres,
            status = status,
            cover = cover ?: manga.cover,
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                getItem(gId) { getString(R.string.id) },
                getItem(gToken) { getString(R.string.token) },
                getItem(exh) { getString(R.string.is_exhentai_gallery) },
                getItem(thumbnailUrl) { getString(R.string.thumbnail_url) },
                getItem(title) { getString(R.string.title) },
                getItem(altTitle) { getString(R.string.alt_title) },
                getItem(genre) { getString(R.string.genre) },
                getItem(datePosted, { MetadataUtil.EX_DATE_FORMAT.format(Date(it)) }) { getString(R.string.date_posted) },
                getItem(parent) { getString(R.string.parent) },
                getItem(visible) { getString(R.string.visible) },
                getItem(language) { getString(R.string.language) },
                getItem(translated) { getString(R.string.translated) },
                getItem(size, { MetadataUtil.humanReadableByteCount(it, true) }) { getString(R.string.gallery_size) },
                getItem(length) { getString(R.string.page_count) },
                getItem(favorites) { getString(R.string.total_favorites) },
                getItem(ratingCount) { getString(R.string.total_ratings) },
                getItem(averageRating) { getString(R.string.average_rating) },
                getItem(aged) { getString(R.string.aged) },
                getItem(lastUpdateCheck, { MetadataUtil.EX_DATE_FORMAT.format(Date(it)) }) { getString(R.string.last_update_check) },
            )
        }
    }

    companion object {
        private const val TITLE_TYPE_TITLE = 0
        private const val TITLE_TYPE_ALT_TITLE = 1

        const val TAG_TYPE_NORMAL = 0
        const val TAG_TYPE_LIGHT = 1
        const val TAG_TYPE_WEAK = 2

        const val EH_GENRE_NAMESPACE = "genre"
        private const val EH_ARTIST_NAMESPACE = "artist"
        const val EH_LANGUAGE_NAMESPACE = "language"
        const val EH_META_NAMESPACE = "meta"
        const val EH_UPLOADER_NAMESPACE = "uploader"
        const val EH_VISIBILITY_NAMESPACE = "visibility"

        private fun splitGalleryUrl(url: String) =
            url.let {
                // Only parse URL if is full URL
                val pathSegments = if (it.startsWith("http")) {
                    it.toUri().pathSegments
                } else {
                    it.split('/')
                }
                pathSegments.filterNot(String::isNullOrBlank)
            }

        fun galleryId(url: String): String = splitGalleryUrl(url)[1]

        fun galleryToken(url: String): String =
            splitGalleryUrl(url)[2]

        fun normalizeUrl(url: String) =
            idAndTokenToUrl(galleryId(url), galleryToken(url))

        fun idAndTokenToUrl(id: String, token: String) =
            "/g/$id/$token/?nw=always"
    }
}
