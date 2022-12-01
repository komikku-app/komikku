package exh.md.follows

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController

/**
 * Controller that shows the latest manga from the catalogue. Inherit [BrowseSourceController].
 */
class MangaDexFollowsController(bundle: Bundle) : BasicFullComposeController(bundle) {

    constructor(source: CatalogueSource) : this(
        bundleOf(
            SOURCE_ID_KEY to source.id,
        ),
    )

    private val sourceId = args.getLong(SOURCE_ID_KEY)

    @Composable
    override fun ComposeContent() {
        Navigator(screen = MangaDexFollowsScreen(sourceId))
    }
}

private const val SOURCE_ID_KEY = "source_id"
