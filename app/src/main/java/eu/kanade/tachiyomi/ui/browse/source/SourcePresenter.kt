package eu.kanade.tachiyomi.ui.browse.source

import android.os.Bundle
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import rx.Observable
import rx.Subscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap

/**
 * Presenter of [SourceController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param sourceManager manages the different sources.
 * @param preferences application preferences.
 */
class SourcePresenter(
    val sourceManager: SourceManager = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    // SY -->
    private val controllerMode: SourceController.Mode
    // SY <--
) : BasePresenter<SourceController>() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    var sources = getEnabledSources()

    /**
     * Subscription for retrieving enabled sources.
     */
    private var sourceSubscription: Subscription? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        // Load enabled and last used sources
        loadSources()
        loadLastUsedSource()
    }

    /**
     * Unsubscribe and create a new subscription to fetch enabled sources.
     */
    private fun loadSources() {
        sourceSubscription?.unsubscribe()

        val pinnedSources = mutableListOf<SourceItem>()
        val pinnedSourceIds = preferences.pinnedSources().get()

        // SY -->
        val categories = mutableListOf<SourceCategory>()

        preferences.sourcesTabCategories().get().sortedByDescending { it.toLowerCase() }.forEach {
            categories.add(SourceCategory(it))
        }

        val sourcesAndCategoriesCombined = preferences.sourcesTabSourcesInCategories().get()
        val sourcesAndCategories = if (sourcesAndCategoriesCombined.isNotEmpty()) sourcesAndCategoriesCombined.map {
            val temp = it.split("|")
            Pair(temp[0], temp[1])
        } else null

        val sourcesInCategories = sourcesAndCategories?.map { it.first }
        // SY <--

        val map = TreeMap<String, MutableList<CatalogueSource>> { d1, d2 ->
            // Catalogues without a lang defined will be placed at the end
            when {
                d1 == "" && d2 != "" -> 1
                d2 == "" && d1 != "" -> -1
                else -> d1.compareTo(d2)
            }
        }
        val byLang = sources.groupByTo(map, { it.lang })
        var sourceItems = byLang.flatMap {
            val langItem = LangItem(it.key)
            it.value.map { source ->
                val isPinned = source.id.toString() in pinnedSourceIds
                if (isPinned) {
                    pinnedSources.add(SourceItem(source, LangItem(PINNED_KEY), isPinned, controllerMode == SourceController.Mode.CATALOGUE))
                }

                // SY -->
                if (sourcesInCategories != null && source.id.toString() in sourcesInCategories) {
                    sourcesAndCategories
                        .filter { SourcesAndCategory -> SourcesAndCategory.first == source.id.toString() }
                        .forEach { SourceAndCategory ->
                            categories.forEach { dataClass ->
                                if (dataClass.category.trim() == SourceAndCategory.second.trim()) {
                                    dataClass.sources.add(
                                        SourceItem(
                                            source,
                                            LangItem("custom|" + SourceAndCategory.second),
                                            isPinned,
                                            controllerMode == SourceController.Mode.CATALOGUE
                                        )
                                    )
                                }
                            }
                        }
                }
                // SY <--

                SourceItem(source, langItem, isPinned, controllerMode == SourceController.Mode.CATALOGUE)
            }
        }

        // SY -->
        categories.forEach {
            sourceItems = it.sources.sortedBy { sourceItem -> sourceItem.source.name.toLowerCase() } + sourceItems
        }
        // SY <--

        if (pinnedSources.isNotEmpty()) {
            sourceItems = pinnedSources + sourceItems
        }

        sourceSubscription = Observable.just(sourceItems)
            .subscribeLatestCache(SourceController::setSources)
    }

    private fun loadLastUsedSource() {
        // Immediate initial load
        preferences.lastUsedSource().get().let { updateLastUsedSource(it) }

        // Subsequent updates
        preferences.lastUsedSource().asFlow()
            .drop(1)
            .onStart { delay(500) }
            .distinctUntilChanged()
            .onEach { updateLastUsedSource(it) }
            .launchIn(scope)
    }

    private fun updateLastUsedSource(sourceId: Long) {
        val source = (sourceManager.get(sourceId) as? CatalogueSource)?.let {
            val isPinned = it.id.toString() in preferences.pinnedSources().get()
            SourceItem(it, null, isPinned, controllerMode == SourceController.Mode.CATALOGUE)
        }
        source?.let { view?.setLastUsedSource(it) }
    }

    fun updateSources() {
        sources = getEnabledSources()
        loadSources()
        loadLastUsedSource()
    }

    /**
     * Returns a list of enabled sources ordered by language and name.
     *
     * @return list containing enabled sources.
     */
    private fun getEnabledSources(): List<CatalogueSource> {
        val languages = preferences.enabledLanguages().get()
        val disabledSourceIds = preferences.disabledSources().get()

        return sourceManager.getVisibleCatalogueSources()
            .filter { it.lang in languages }
            .filterNot { it.id.toString() in disabledSourceIds }
            .sortedBy { "(${it.lang}) ${it.name}" } +
            sourceManager.get(LocalSource.ID) as LocalSource
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"
    }
}

// SY -->
data class SourceCategory(val category: String, var sources: MutableList<SourceItem> = mutableListOf())
// SY <--
