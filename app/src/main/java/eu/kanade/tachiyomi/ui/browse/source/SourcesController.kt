package eu.kanade.tachiyomi.ui.browse.source

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.compose.runtime.Composable
import eu.kanade.presentation.browse.BrowseTabWrapper
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.requestPermissionsSafe
import eu.kanade.tachiyomi.util.system.getParcelableCompat
import kotlinx.parcelize.Parcelize

class SourcesController(bundle: Bundle? = null) : FullComposeController<SourcesPresenterWrapper>(bundle) {
    private val smartSearchConfig = args.getParcelableCompat<SmartSearchConfig>(SMART_SEARCH_CONFIG)
    private val mode = if (smartSearchConfig == null) Mode.CATALOGUE else Mode.SMART_SEARCH

    override fun getTitle(): String? {
        // SY -->
        return when (mode) {
            Mode.CATALOGUE -> applicationContext?.getString(R.string.label_sources)
            Mode.SMART_SEARCH -> applicationContext?.getString(R.string.find_in_another_source)
        }
        // SY <--
    }

    override fun createPresenter() = SourcesPresenterWrapper(controllerMode = mode, smartSearchConfig = smartSearchConfig)

    @Composable
    override fun ComposeContent() {
        BrowseTabWrapper(sourcesTab(router, presenter = presenter.presenter))
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        requestPermissionsSafe(arrayOf(WRITE_EXTERNAL_STORAGE), 301)
    }

    @Parcelize
    data class SmartSearchConfig(val origTitle: String, val origMangaId: Long? = null) : Parcelable

    enum class Mode {
        CATALOGUE,
        SMART_SEARCH
    }

    companion object {
        const val SMART_SEARCH_CONFIG = "SMART_SEARCH_CONFIG"
        const val SMART_SEARCH_SOURCE_TAG = "smart_search_source_tag"
    }
}
