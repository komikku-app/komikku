package eu.kanade.tachiyomi.ui.video

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class VideoPresenter(
    private val db: DatabaseHelper = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get()
) : BasePresenter<VideoActivity>() {
    var episode: Chapter? = null
        private set

    fun needsInit(): Boolean {
        return episode == null
    }

    fun init(episodeId: Long) {
        if (!needsInit()) return

        db.getChapter(episodeId).asRxObservable()
            .first()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { init(it) }
            .subscribeFirst(
                { _, _ ->
                    // Ignore onNext event
                },
                VideoActivity::initError
            )
    }
    fun init(initEpisode: Chapter) {
        episode = initEpisode
    }
}
