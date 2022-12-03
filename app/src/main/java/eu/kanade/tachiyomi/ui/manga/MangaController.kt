package eu.kanade.tachiyomi.ui.manga

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.util.system.getSerializableCompat

class MangaController : BasicFullComposeController {

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getLong(MANGA_EXTRA))

    constructor(
        mangaId: Long,
        fromSource: Boolean = false,
        smartSearchConfig: SourcesScreen.SmartSearchConfig? = null,
    ) : super(bundleOf(MANGA_EXTRA to mangaId, FROM_SOURCE_EXTRA to fromSource, SMART_SEARCH_CONFIG_EXTRA to smartSearchConfig))

    // SY -->
    constructor(redirect: MangaInfoScreenModel.EXHRedirect) : super(
        bundleOf(MANGA_EXTRA to redirect.mangaId),
    )
    // SY <--

    val mangaId: Long
        get() = args.getLong(MANGA_EXTRA)

    val fromSource: Boolean
        get() = args.getBoolean(FROM_SOURCE_EXTRA)

    // SY -->
    val smartSearchConfig: SourcesScreen.SmartSearchConfig?
        get() = args.getSerializableCompat(SMART_SEARCH_CONFIG_EXTRA)
    // SY <--

    @Composable
    override fun ComposeContent() {
        Navigator(screen = MangaScreen(mangaId, fromSource, smartSearchConfig))
    }

    companion object {
        const val FROM_SOURCE_EXTRA = "from_source"
        const val MANGA_EXTRA = "manga"

        // EXH -->
        const val UPDATE_EXTRA = "update"
        const val SMART_SEARCH_CONFIG_EXTRA = "smartSearchConfig"
        // EXH <--
    }
}
