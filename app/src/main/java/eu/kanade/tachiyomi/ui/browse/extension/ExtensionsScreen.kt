package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.presentation.browse.BrowseTabWrapper
import eu.kanade.presentation.util.Screen

/**
 * An alternative ExtensionsScreen used when want to open [extensionsTab] directly
 */
class ExtensionsScreen : Screen() {
    @Composable
    override fun Content() {
        val extensionsScreenModel = rememberScreenModel { ExtensionsScreenModel() }
        BrowseTabWrapper(extensionsTab(extensionsScreenModel))
    }
}
