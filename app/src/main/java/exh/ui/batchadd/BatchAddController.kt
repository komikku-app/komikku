package exh.ui.batchadd

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

/**
 * Batch add screen
 */
class BatchAddController : BasicFullComposeController() {

    @Composable
    override fun ComposeContent() {
        Navigator(screen = BatchAddScreen())
    }
}
