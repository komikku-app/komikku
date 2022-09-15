package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.source.R
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.copy
import exh.md.utils.MangaDexRelation
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable

@Serializable
class MangaDexSearchMetadata : RaisedSearchMetadata() {
    var mdUuid: String? = null

    // var mdUrl: String? = null

    var cover: String? = null

    var title: String? by titleDelegate(TITLE_TYPE_MAIN)
    var altTitles: List<String>? = null

    var description: String? = null

    var authors: List<String>? = null
    var artists: List<String>? = null

    var langFlag: String? = null

    var lastChapterNumber: Int? = null
    var rating: Float? = null
    // var users: String? = null

    var anilistId: String? = null
    var kitsuId: String? = null
    var myAnimeListId: String? = null
    var mangaUpdatesId: String? = null
    var animePlanetId: String? = null

    var status: Int? = null

    // var missing_chapters: String? = null

    var followStatus: Int? = null
    var relation: MangaDexRelation? = null

    // var maxChapterNumber: Int? = null

    override fun createMangaInfo(manga: SManga): SManga {
        val key = mdUuid?.let { "/manga/$it" }

        val title = title

        val cover = cover

        val author = authors?.joinToString()

        val artist = artists?.joinToString()

        val status = status

        val genres = tagsToGenreString()

        val description = description

        return manga.copy(
            url = key ?: manga.url,
            title = title ?: manga.title,
            thumbnail_url = cover ?: manga.thumbnail_url,
            author = author ?: manga.author,
            artist = artist ?: manga.artist,
            status = status ?: manga.status,
            genre = genres,
            description = description ?: manga.description,
        )
    }

    override fun getExtraInfoPairs(context: Context): List<Pair<String, String>> {
        return with(context) {
            listOfNotNull(
                getItem(mdUuid) { getString(R.string.id) },
                // getItem(mdUrl) { getString(R.string.url) },
                getItem(cover) { getString(R.string.thumbnail_url) },
                getItem(title) { getString(R.string.title) },
                getItem(authors, { it.joinToString() }) { getString(R.string.author) },
                getItem(artists, { it.joinToString() }) { getString(R.string.artist) },
                getItem(langFlag) { getString(R.string.language) },
                getItem(lastChapterNumber) { getString(R.string.last_chapter_number) },
                getItem(rating) { getString(R.string.average_rating) },
                // getItem(users) { getString(R.string.total_ratings) },
                getItem(status) { getString(R.string.status) },
                // getItem(missing_chapters) { getString(R.string.missing_chapters) },
                getItem(followStatus) { getString(R.string.follow_status) },
                getItem(anilistId) { getString(R.string.anilist_id) },
                getItem(kitsuId) { getString(R.string.kitsu_id) },
                getItem(myAnimeListId) { getString(R.string.mal_id) },
                getItem(mangaUpdatesId) { getString(R.string.manga_updates_id) },
                getItem(animePlanetId) { getString(R.string.anime_planet_id) },
            )
        }
    }

    companion object {
        private const val TITLE_TYPE_MAIN = 0

        const val TAG_TYPE_DEFAULT = 0
    }
}
