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
        val title = if (Injekt.get<PreferencesHelper>().useJapaneseTitle().get()) {
            altTitle ?: title
        } else {
            title
        }

        // Set artist (if we can find one)
        val artist = tags.ofNamespace(EH_ARTIST_NAMESPACE).let { tags ->
            if (tags.isNotEmpty()) tags.joinToString(transform = { it.name }) else null
        }

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
            cover = cover ?: manga.cover
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                gId?.let { getString(R.string.id) to it },
                gToken?.let { getString(R.string.token) to it },
                exh?.let { getString(R.string.is_exhentai_gallery) to it.toString() },
                thumbnailUrl?.let { getString(R.string.thumbnail_url) to it },
                title?.let { getString(R.string.title) to it },
                altTitle?.let { getString(R.string.alt_title) to it },
                genre?.let { getString(R.string.genre) to it },
                datePosted?.let { getString(R.string.date_posted) to MetadataUtil.EX_DATE_FORMAT.format(Date(it)) },
                parent?.let { getString(R.string.parent) to it },
                visible?.let { getString(R.string.visible) to it },
                language?.let { getString(R.string.language) to it },
                translated?.let { getString(R.string.translated) to it.toString() },
                size?.let { getString(R.string.gallery_size) to MetadataUtil.humanReadableByteCount(it, true) },
                length?.let { getString(R.string.page_count) to it.toString() },
                favorites?.let { getString(R.string.total_favorites) to it.toString() },
                ratingCount?.let { getString(R.string.total_ratings) to it.toString() },
                averageRating?.let { getString(R.string.average_rating) to it.toString() },
                aged.let { getString(R.string.aged) to it.toString() },
                lastUpdateCheck.let { getString(R.string.last_update_check) to MetadataUtil.EX_DATE_FORMAT.format(Date(it)) },
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
