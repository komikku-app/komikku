package exh.pagepreview

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

class PagePreviewController : BasicFullComposeController {

    @Suppress("unused")
    constructor(bundle: Bundle? = null) : super(bundle)

    constructor(mangaId: Long) : super(
        bundleOf(MANGA_ID to mangaId),
    )

    @Composable
    override fun ComposeContent() {
        Navigator(screen = PagePreviewScreen(args.getLong(MANGA_ID, -1)))
    }

    companion object {
        const val MANGA_ID = "manga_id"
    }
}
