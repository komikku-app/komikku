package exh.ui.smartsearch

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.browse.source.SourcesController
import eu.kanade.tachiyomi.util.system.getSerializableCompat

class SmartSearchController(bundle: Bundle) : BasicFullComposeController() {
    private val sourceId = bundle.getLong(ARG_SOURCE_ID, -1)
    private val smartSearchConfig = bundle.getSerializableCompat<SourcesController.SmartSearchConfig>(ARG_SMART_SEARCH_CONFIG)!!

    constructor(sourceId: Long, smartSearchConfig: SourcesController.SmartSearchConfig) : this(
        bundleOf(
            ARG_SOURCE_ID to sourceId,
            ARG_SMART_SEARCH_CONFIG to smartSearchConfig,
        ),
    )

    @Composable
    override fun ComposeContent() {
        Navigator(screen = SmartSearchScreen(sourceId, smartSearchConfig))
    }

    companion object {
        private const val ARG_SOURCE_ID = "SOURCE_ID"
        private const val ARG_SMART_SEARCH_CONFIG = "SMART_SEARCH_CONFIG"
    }
}
