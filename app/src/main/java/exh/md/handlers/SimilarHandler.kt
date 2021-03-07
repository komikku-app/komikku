package exh.md.handlers

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.similar.sql.models.MangaSimilar
import exh.md.similar.sql.models.MangaSimilarImpl
import exh.md.utils.MdUtil
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SimilarHandler(val preferences: PreferencesHelper, private val useLowQualityCovers: Boolean) {

    /*
     * fetch our similar mangas
     */
    fun fetchSimilar(manga: Manga): Observable<MangasPage> {
        // Parse the Mangadex id from the URL
        return Observable.just(MdUtil.getMangaId(manga.url).toLong())
            .flatMap { mangaId ->
                Injekt.get<DatabaseHelper>().getSimilar(mangaId).asRxObservable()
            }.map { similarMangaDb: MangaSimilar? ->
                if (similarMangaDb != null) {
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
}
