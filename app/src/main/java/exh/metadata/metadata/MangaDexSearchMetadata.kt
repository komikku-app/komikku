package exh.metadata.metadata

import android.content.Context
import eu.kanade.tachiyomi.R
import exh.md.utils.MangaDexRelation
import exh.md.utils.MdUtil
import exh.metadata.metadata.base.RaisedSearchMetadata
import kotlinx.serialization.Serializable
import tachiyomi.source.model.MangaInfo

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

    override fun createMangaInfo(manga: MangaInfo): MangaInfo {
        val key = mdUuid?.let { MdUtil.buildMangaUrl(it) }

        val title = title

        val cover = cover

        val author = authors?.joinToString()?.let { MdUtil.cleanString(it) }

        val artist = artists?.joinToString()?.let { MdUtil.cleanString(it) }

        val status = status

        val genres = tagsToGenreList()

        val description = description

        return manga.copy(
            key = key ?: manga.key,
            title = title ?: manga.title,
            cover = cover ?: manga.cover,
            author = author ?: manga.author,
            artist = artist ?: manga.artist,
            status = status ?: manga.status,
            genres = genres,
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
