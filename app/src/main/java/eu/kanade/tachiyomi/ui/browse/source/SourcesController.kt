package eu.kanade.tachiyomi.ui.browse.source

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.base.controller.requestPermissionsSafe
import eu.kanade.tachiyomi.util.system.getSerializableCompat
import java.io.Serializable

class SourcesController(bundle: Bundle? = null) : BasicFullComposeController(bundle) {
    private val smartSearchConfig = args.getSerializableCompat<SmartSearchConfig>(SMART_SEARCH_CONFIG)

    @Composable
    override fun ComposeContent() {
        Navigator(screen = SourcesScreen(smartSearchConfig))
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        requestPermissionsSafe(arrayOf(WRITE_EXTERNAL_STORAGE), 301)
    }

    data class SmartSearchConfig(val origTitle: String, val origMangaId: Long? = null) : Serializable

    companion object {
        const val SMART_SEARCH_CONFIG = "SMART_SEARCH_CONFIG"
        const val SMART_SEARCH_SOURCE_TAG = "smart_search_source_tag"
    }
}
