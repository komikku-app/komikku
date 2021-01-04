package eu.kanade.tachiyomi.ui.video

import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.VideoActivityBinding
import eu.kanade.tachiyomi.ui.base.activity.BaseRxActivity
import nucleus.factory.RequiresPresenter

@RequiresPresenter(VideoPresenter::class)
class VideoActivity : BaseRxActivity<VideoActivityBinding, VideoPresenter>() {

    companion object {
        @Suppress("unused")
        fun newIntent(context: Context, anime: Manga, episode: Chapter): Intent {
            return Intent(context, VideoActivity::class.java).apply {
                putExtra("anime", anime.id)
                putExtra("episode", episode.id)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}
