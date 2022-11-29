package eu.kanade.tachiyomi.ui.browse.source.feed

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

class SourceFeedController : BasicFullComposeController {

    constructor(source: CatalogueSource) : super(
        bundleOf(
            SOURCE_EXTRA to source.id,
        ),
    )

    constructor(sourceId: Long) : super(
        bundleOf(
            SOURCE_EXTRA to sourceId,
        ),
    )

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle)

    val sourceId = args.getLong(SOURCE_EXTRA)

    @Composable
    override fun ComposeContent() {
        Navigator(screen = SourceFeedScreen(sourceId))
    }

    companion object {
        const val SOURCE_EXTRA = "source"
    }
}
