package eu.kanade.tachiyomi.ui.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.VideoActivityBinding
import eu.kanade.tachiyomi.ui.base.activity.BaseRxActivity
import eu.kanade.tachiyomi.util.system.toast
import nucleus.factory.RequiresPresenter
import timber.log.Timber

@RequiresPresenter(VideoPresenter::class)
class VideoActivity : BaseRxActivity<VideoActivityBinding, VideoPresenter>() {
    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = VideoActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (presenter.needsInit()) {
            val episode = intent.extras!!.getLong("episode", -1)
            if (episode == -1L) {
                finish()
                return
            }
            presenter.init(episode)
        }
    }

    fun initError(error: Throwable) {
        Timber.e(error)
        finish()
        toast(error.message)
    }

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
