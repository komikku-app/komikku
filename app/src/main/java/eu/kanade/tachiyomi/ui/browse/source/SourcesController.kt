package eu.kanade.tachiyomi.ui.browse.source

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import com.bluelinelabs.conductor.Controller
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.controller.SearchableComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.base.controller.requestPermissionsSafe
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.browse.source.latest.LatestUpdatesController
import eu.kanade.tachiyomi.ui.main.MainActivity
import exh.ui.smartsearch.SmartSearchController
import kotlinx.parcelize.Parcelize
import uy.kohesive.injekt.injectLazy

class SourcesController(bundle: Bundle? = null) : SearchableComposeController<SourcesPresenter>(bundle) {

    private val preferences: PreferencesHelper by injectLazy()

    // EXH -->
    private val smartSearchConfig: SmartSearchConfig? = args.getParcelable(SMART_SEARCH_CONFIG)

    private val mode = if (smartSearchConfig == null) Mode.CATALOGUE else Mode.SMART_SEARCH
    // EXH <--

    init {
        // SY -->
        setHasOptionsMenu(mode == Mode.CATALOGUE)
        // SY <--
    }

    override fun getTitle(): String? {
        // SY -->
        return when (mode) {
            Mode.CATALOGUE -> applicationContext?.getString(R.string.label_sources)
            Mode.SMART_SEARCH -> applicationContext?.getString(R.string.find_in_another_source)
        }
        // SY <--
    }

    override fun createPresenter() = SourcesPresenter(/* SY --> */ controllerMode = mode /* SY <-- */)

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        SourcesScreen(
            nestedScrollInterop = nestedScrollInterop,
            presenter = presenter,
            onClickItem = { source ->
                when {
                    mode == Mode.SMART_SEARCH -> router.pushController(SmartSearchController(source.id, smartSearchConfig!!))
                    preferences.useNewSourceNavigation().get() -> openSource(source, SourceFeedController(source.id))
                    else -> openSource(source, BrowseSourceController(source))
                }
            },
            onClickDisable = { source ->
                presenter.toggleSource(source)
            },
            onClickLatest = { source ->
                openSource(source, LatestUpdatesController(source))
            },
            onClickPin = { source ->
                presenter.togglePin(source)
            },
            onClickSetCategories = { source, categories ->
                presenter.setSourceCategories(source, categories)
            },
            onClickToggleDataSaver = { source ->
                presenter.toggleExcludeFromDataSaver(source)
            },
        )

        LaunchedEffect(Unit) {
            (activity as? MainActivity)?.ready = true
        }
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        requestPermissionsSafe(arrayOf(WRITE_EXTERNAL_STORAGE), 301)
    }

    /**
     * Opens a catalogue with the given controller.
     */
    private fun openSource(source: Source, controller: Controller) {
        if (!preferences.incognitoMode().get()) {
            preferences.lastUsedSource().set(source.id)
        }
        parentController!!.router.pushController(controller)
    }

    /**
     * Called when an option menu item has been selected by the user.
     *
     * @param item The selected item.
     * @return True if this event has been consumed, false if it has not.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // Initialize option to open catalogue settings.
            R.id.action_settings -> {
                parentController!!.router.pushController(SourceFilterController())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (mode == Mode.CATALOGUE) {
            createOptionsMenu(
                menu,
                inflater,
                R.menu.browse_sources,
                R.id.action_search,
                R.string.action_global_search_hint,
                false, // GlobalSearch handles the searching here
            )
        }
    }

    override fun onSearchViewQueryTextSubmit(query: String?) {
        // SY -->
        if (mode == Mode.CATALOGUE) {
            parentController!!.router.pushController(
                GlobalSearchController(query),
            )
        }
        // SY <--
    }

    // SY -->
    @Parcelize
    data class SmartSearchConfig(val origTitle: String, val origMangaId: Long? = null) : Parcelable

    enum class Mode {
        CATALOGUE,
        SMART_SEARCH
    }

    companion object {
        const val SMART_SEARCH_CONFIG = "SMART_SEARCH_CONFIG"
        const val SMART_SEARCH_SOURCE_TAG = "smart_search_source_tag"
    }
    // SY <--
}
