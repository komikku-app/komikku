package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable

@Serializable
class EightMusesSearchMetadata : RaisedSearchMetadata() {
    var path: List<String> = emptyList()

    var title by titleDelegate(TITLE_TYPE_MAIN)

    var thumbnailUrl: String? = null

    override fun copyTo(manga: SManga) {
        manga.url = path.joinToString("/", prefix = "/")

        title?.let {
            manga.title = it
        }

        thumbnailUrl?.let {
            manga.thumbnail_url = it
        }

        manga.artist = tags.ofNamespace(ARTIST_NAMESPACE).joinToString { it.name }

        manga.genre = tagsToGenreString()

        /*val titleDesc = StringBuilder()
        title?.let { titleDesc += "Title: $it\n" }

        val tagsDesc = tagsToDescription()*/

        manga.description = "meta" /*listOf(titleDesc.toString(), tagsDesc.toString())
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")*/
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        title?.let { pairs += Pair(context.getString(R.string.title), it) }
        val path = path.joinToString("/", prefix = "/")
        if (path.isNotBlank()) {
            pairs += Pair(context.getString(R.string.path), path)
        }
        thumbnailUrl?.let { pairs += Pair(context.getString(R.string.thumbnail_url), it) }
        return pairs
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0

        const val TAGS_NAMESPACE = "tags"
        const val ARTIST_NAMESPACE = "artist"
    }
}
