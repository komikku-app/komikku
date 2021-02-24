package exh.ui.smartsearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.databinding.EhSmartSearchBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.source.SourceController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import uy.kohesive.injekt.injectLazy

class SmartSearchController(bundle: Bundle? = null) : NucleusController<EhSmartSearchBinding, SmartSearchPresenter>() {
    private val sourceManager: SourceManager by injectLazy()

    private val source = sourceManager.get(bundle?.getLong(ARG_SOURCE_ID, -1) ?: -1) as? CatalogueSource
    private val smartSearchConfig: SourceController.SmartSearchConfig? = bundle?.getParcelable(
        ARG_SMART_SEARCH_CONFIG
    )

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = EhSmartSearchBinding.inflate(inflater)
        return binding.root
    }

    override fun getTitle() = source?.name.orEmpty()

    override fun createPresenter() = SmartSearchPresenter(source!!, smartSearchConfig!!)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.appbar.bringToFront()

        if (source == null || smartSearchConfig == null) {
            router.popCurrentController()
            applicationContext?.toast("Missing data!")
            return
        }

        presenter.smartSearchFlow
            .onEach { results ->
                if (results is SmartSearchPresenter.SearchResults.Found) {
                    val transaction = MangaController(results.manga, true, smartSearchConfig).withFadeTransaction()
                    withUIContext {
                        router.replaceTopController(transaction)
                    }
                } else {
                    withUIContext {
                        if (results is SmartSearchPresenter.SearchResults.NotFound) {
                            applicationContext?.toast("Couldn't find the manga in the source!")
                        } else {
                            applicationContext?.toast("Error performing automatic search!")
                        }
                    }

                    val transaction = BrowseSourceController(source, smartSearchConfig.origTitle, smartSearchConfig).withFadeTransaction()
                    withUIContext {
                        router.replaceTopController(transaction)
                    }
                }
            }
            .launchIn(viewScope + Dispatchers.IO)
    }

    companion object {
        const val ARG_SOURCE_ID = "SOURCE_ID"
        const val ARG_SMART_SEARCH_CONFIG = "SMART_SEARCH_CONFIG"
    }
}
