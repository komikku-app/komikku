package eu.kanade.tachiyomi.ui.browse.source

import android.os.Bundle
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.util.system.getSerializableCompat
import java.io.Serializable

class SourcesController(bundle: Bundle? = null) : BasicFullComposeController(bundle) {
    private val smartSearchConfig = args.getSerializableCompat<SmartSearchConfig>(SMART_SEARCH_CONFIG)

    @Composable
    override fun ComposeContent() {
        Navigator(screen = SourcesScreen(smartSearchConfig))
    }

    data class SmartSearchConfig(val origTitle: String, val origMangaId: Long? = null) : Serializable

    companion object {
        const val SMART_SEARCH_CONFIG = "SMART_SEARCH_CONFIG"
    }
}
