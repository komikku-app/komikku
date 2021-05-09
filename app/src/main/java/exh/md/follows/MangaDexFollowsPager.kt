package exh.md.follows

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.Pager
import eu.kanade.tachiyomi.util.lang.runAsObservable
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * LatestUpdatesPager inherited from the general Pager.
 */
class MangaDexFollowsPager(val source: MangaDex) : Pager() {

    override fun requestNext(): Observable<MangasPage> {
        return runAsObservable({ source.fetchFollows(currentPage) })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { onPageReceived(it) }
    }
}
