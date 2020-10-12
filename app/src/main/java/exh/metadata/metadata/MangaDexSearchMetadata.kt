package exh.metadata.metadata

import android.content.Context
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable

@Serializable
class MangaDexSearchMetadata : RaisedSearchMetadata() {
    var mdId: String? = null

    var mdUrl: String? = null

    var thumbnail_url: String? = null

    var title: String? by titleDelegate(TITLE_TYPE_MAIN)

    var description: String? = null

    var author: String? = null
    var artist: String? = null

    var lang_flag: String? = null

    var last_chapter_number: Int? = null
    var rating: String? = null
    var users: String? = null

    var anilist_id: String? = null
    var kitsu_id: String? = null
    var my_anime_list_id: String? = null
    var manga_updates_id: String? = null
    var anime_planet_id: String? = null

    var status: Int? = null

    var missing_chapters: String? = null

    var follow_status: Int? = null

    override fun copyTo(manga: SManga) {
        mdUrl?.let {
            manga.url = try {
                val uri = it.toUri()
                val out = uri.path!!.removePrefix("/api")
                out + if (out.endsWith("/")) "" else "/"
            } catch (e: Exception) {
                it
            }
        }

        title?.let {
            manga.title = it
        }

        // Guess thumbnail URL if manga does not have thumbnail URL

        manga.thumbnail_url = thumbnail_url

        author?.let {
            manga.author = it
        }

        artist?.let {
            manga.artist = it
        }

        status?.let {
            manga.status = it
        }

        manga.genre = tagsToGenreString()

        description?.let {
            manga.description = it
        }
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        mdId?.let { pairs += Pair(context.getString(R.string.id), it) }
        mdUrl?.let { pairs += Pair(context.getString(R.string.url), it) }
        thumbnail_url?.let { pairs += Pair(context.getString(R.string.thumbnail_url), it) }
        title?.let { pairs += Pair(context.getString(R.string.title), it) }
        author?.let { pairs += Pair(context.getString(R.string.author), it) }
        artist?.let { pairs += Pair(context.getString(R.string.artist), it) }
        lang_flag?.let { pairs += Pair(context.getString(R.string.language), it) }
        last_chapter_number?.let { pairs += Pair(context.getString(R.string.last_chapter_number), it.toString()) }
        rating?.let { pairs += Pair(context.getString(R.string.average_rating), it) }
        users?.let { pairs += Pair(context.getString(R.string.total_ratings), it) }
        status?.let { pairs += Pair(context.getString(R.string.status), it.toString()) }
        missing_chapters?.let { pairs += Pair(context.getString(R.string.missing_chapters), it) }
        follow_status?.let { pairs += Pair(context.getString(R.string.follow_status), it.toString()) }
        anilist_id?.let { pairs += Pair(context.getString(R.string.anilist_id), it) }
        kitsu_id?.let { pairs += Pair(context.getString(R.string.kitsu_id), it) }
        my_anime_list_id?.let { pairs += Pair(context.getString(R.string.mal_id), it) }
        manga_updates_id?.let { pairs += Pair(context.getString(R.string.manga_updates_id), it) }
        anime_planet_id?.let { pairs += Pair(context.getString(R.string.anime_planet_id), it) }
        return pairs
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0
    }
}
