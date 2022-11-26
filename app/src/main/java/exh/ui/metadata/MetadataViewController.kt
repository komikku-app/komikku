package exh.ui.metadata

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

class MetadataViewController : BasicFullComposeController {
    constructor(manga: Manga) : super(
        bundleOf(
            MANGA_EXTRA to manga.id,
            SOURCE_EXTRA to manga.source,
        ),
    )

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle)

    @Composable
    override fun ComposeContent() {
        Navigator(screen = MetadataViewScreen(args.getLong(MANGA_EXTRA), args.getLong(SOURCE_EXTRA)))
    }

    companion object {
        const val MANGA_EXTRA = "manga"
        const val SOURCE_EXTRA = "source"
    }
}
