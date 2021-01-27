package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable
import tachiyomi.source.model.MangaInfo

@Serializable
class PururinSearchMetadata : RaisedSearchMetadata() {
    var prId: Int? = null

    var prShortLink: String? = null

    var title by titleDelegate(TITLE_TYPE_TITLE)
    var altTitle by titleDelegate(TITLE_TYPE_ALT_TITLE)

    var thumbnailUrl: String? = null

    var uploaderDisp: String? = null

    var pages: Int? = null

    var fileSize: String? = null

    var ratingCount: Int? = null
    var averageRating: Double? = null

    override fun createMangaInfo(manga: MangaInfo): MangaInfo {
        val key = prId?.let { prId ->
            prShortLink?.let { prShortLink ->
                "/gallery/$prId/$prShortLink"
            }
        }

        val title = title ?: altTitle

        val cover = thumbnailUrl

        val artist = tags.ofNamespace(TAG_NAMESPACE_ARTIST).joinToString { it.name }

        val genres = tagsToGenreList()

        val description = "meta"

        return manga.copy(
            key = key ?: manga.key,
            title = title ?: manga.title,
            cover = cover ?: manga.cover,
            artist = artist,
            genres = genres,
            description = description
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        prId?.let { pairs += context.getString(R.string.id) to it.toString() }
        title?.let { pairs += context.getString(R.string.title) to it }
        altTitle?.let { pairs += context.getString(R.string.alt_title) to it }
        thumbnailUrl?.let { pairs += context.getString(R.string.thumbnail_url) to it }
        uploaderDisp?.let { pairs += context.getString(R.string.uploader_capital) to it }
        uploader?.let { pairs += context.getString(R.string.uploader) to it }
        pages?.let { pairs += context.getString(R.string.page_count) to it.toString() }
        fileSize?.let { pairs += context.getString(R.string.gallery_size) to it }
        ratingCount?.let { pairs += context.getString(R.string.total_ratings) to it.toString() }
        averageRating?.let { pairs += context.getString(R.string.average_rating) to it.toString() }
        return pairs
    }

    companion object {
        private const val TITLE_TYPE_TITLE = 0
        private const val TITLE_TYPE_ALT_TITLE = 1

        const val TAG_TYPE_DEFAULT = 0

        private const val TAG_NAMESPACE_ARTIST = "artist"
        const val TAG_NAMESPACE_CATEGORY = "category"

        const val BASE_URL = "https://pururin.io"
    }
}
