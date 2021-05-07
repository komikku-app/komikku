package exh.md.handlers

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.similar.sql.models.MangaSimilarImpl
import exh.md.utils.MdUtil
import exh.util.executeOnIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SimilarHandler(val preferences: PreferencesHelper, private val useLowQualityCovers: Boolean) {

    /**
     * fetch our similar mangas
     */
    suspend fun fetchSimilar(manga: Manga): MangasPage {
        // Parse the Mangadex id from the URL
        val mangaId = MdUtil.getMangaId(manga.url).toLong()
        val similarMangaDb = Injekt.get<DatabaseHelper>().getSimilar(mangaId).executeOnIO()
        return if (similarMangaDb != null) {
            val similarMangaTitles = similarMangaDb.matched_titles.split(MangaSimilarImpl.DELIMITER)
            val similarMangaIds = similarMangaDb.matched_ids.split(MangaSimilarImpl.DELIMITER)
            val similarMangas = similarMangaIds.mapIndexed { index, similarId ->
                SManga.create().apply {
                    title = similarMangaTitles[index]
                    url = "/manga/$similarId/"
                    thumbnail_url = MdUtil.formThumbUrl(url, useLowQualityCovers)
                }
            }
            MangasPage(similarMangas, false)
        } else MangasPage(mutableListOf(), false)
    }
}
