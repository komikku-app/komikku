package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.source.R
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.copy
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.util.nullIfEmpty
import kotlinx.serialization.Serializable

@Serializable
class EightMusesSearchMetadata : RaisedSearchMetadata() {
    var path: List<String> = emptyList()

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var thumbnailUrl: String? = null

    override fun createMangaInfo(manga: SManga): SManga {
        val key = path.joinToString("/", prefix = "/")

        val title = title

        val cover = thumbnailUrl

        val artist = tags.ofNamespace(ARTIST_NAMESPACE).joinToString { it.name }

        val genres = tagsToGenreString()

        val description = "meta"

        return manga.copy(
            url = key,
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
                getItem(title) { getString(R.string.title) },
                getItem(path.nullIfEmpty(), { it.joinToString("/", prefix = "/") }) { getString(R.string.path) },
                getItem(thumbnailUrl) { getString(R.string.thumbnail_url) },
            )
        }
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        const val TAGS_NAMESPACE = "tags"
        const val ARTIST_NAMESPACE = "artist"
    }
}
