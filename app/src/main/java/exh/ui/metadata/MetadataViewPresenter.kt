package exh.ui.metadata

import android.os.Bundle
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.getFlatMetadataForManga
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MetadataViewPresenter(
    val manga: Manga,
    val source: Source,
    val preferences: PreferencesHelper = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get()
) : BasePresenter<MetadataViewController>() {

    var meta: RaisedSearchMetadata? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        getMangaMetaObservable().subscribeLatestCache({ view, flatMetadata -> if (flatMetadata != null) view.onNextMetaInfo(flatMetadata) else XLog.nst().d("Invalid metadata") })

        getMangaObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeLatestCache({ view, _ -> view.onNextMangaInfo(meta) })
    }

    private fun getMangaObservable(): Observable<Manga> {
        return db.getManga(manga.url, manga.source).asRxObservable()
    }

    private fun getMangaMetaObservable(): Observable<FlatMetadata?> {
        val mangaId = manga.id
        return if (mangaId != null) {
            db.getFlatMetadataForManga(mangaId).asRxObservable()
                .observeOn(AndroidSchedulers.mainThread())
        } else Observable.just(null)
    }
}
