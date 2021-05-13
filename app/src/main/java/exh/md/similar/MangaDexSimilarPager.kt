package exh.md.similar

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.toMangaInfo
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.NoResultsException
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager
import eu.kanade.tachiyomi.util.lang.runAsObservable
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * MangaDexSimilarPager inherited from the general Pager.
 */
class MangaDexSimilarPager(val manga: Manga, val source: MangaDex) : Pager() {

    override fun requestNext(): Observable<MangasPage> {
        return runAsObservable({ source.getMangaSimilar(manga.toMangaInfo()) })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                if (it.mangas.isNotEmpty()) {
                    onPageReceived(it)
                } else {
                    throw NoResultsException()
                }
            }
    }
}
