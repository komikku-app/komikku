package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.copy
import kotlinx.serialization.Serializable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

@Serializable
class HBrowseSearchMetadata : RaisedSearchMetadata() {
    var hbId: Long? = null

    var hbUrl: String? = null

    var thumbnail: String? = null

    var title: String? by titleDelegate(TITLE_TYPE_MAIN)

    // Length in pages
    var length: Int? = null

    override fun createMangaInfo(manga: SManga): SManga {
        val key = hbUrl

        val title = title

        // Guess thumbnail URL if manga does not have thumbnail URL
        val cover = if (manga.thumbnail_url.isNullOrBlank()) {
            guessThumbnailUrl(hbId.toString())
        } else {
            null
        }

        val artist = tags.ofNamespace(ARTIST_NAMESPACE).joinToString { it.name }

        val genres = tagsToGenreString()

        val description = null

        return manga.copy(
            url = key ?: manga.url,
            title = title ?: manga.title,
            thumbnail_url = cover ?: manga.thumbnail_url,
            artist = artist,
            genre = genres,
            description = description,
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                getItem(hbId) { stringResource(SYMR.strings.id) },
                getItem(hbUrl) { stringResource(SYMR.strings.url) },
                getItem(thumbnail) { stringResource(SYMR.strings.thumbnail_url) },
                getItem(title) { stringResource(MR.strings.title) },
                getItem(length) { stringResource(SYMR.strings.page_count) },
            )
        }
    }

    companion object {
        const val BASE_URL = "https://www.hbrowse.com"

        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0
        const val ARTIST_NAMESPACE = "artist"

        fun guessThumbnailUrl(hbid: String): String {
            return "$BASE_URL/thumbnails/${hbid}_1.jpg#guessed"
        }
    }
}
