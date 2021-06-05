package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import dev.chrisbanes.insetter.applyInsetter
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MigrationSourcesControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.BrowseController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationController
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrationMangaController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.openInBrowser
import exh.util.executeOnIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationSourcesController :
    NucleusController<MigrationSourcesControllerBinding, MigrationSourcesPresenter>(),
    FlexibleAdapter.OnItemClickListener,
    // SY -->
    SourceAdapter.OnAllClickListener {
    // SY <--

    private var adapter: SourceAdapter? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun createPresenter(): MigrationSourcesPresenter {
        return MigrationSourcesPresenter()
    }

    override fun createBinding(inflater: LayoutInflater) = MigrationSourcesControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        adapter = SourceAdapter(this)
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = adapter
        adapter?.fastScroller = binding.fastScroller
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.browse_migrate, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_source_migration_help -> activity?.openInBrowser(HELP_URL)
        }
        return super.onOptionsItemSelected(item)
    }

    fun setSources(sourcesWithManga: List<SourceItem>) {
        adapter?.updateDataSet(sourcesWithManga)
    }

    // SY -->
    override fun getTitle(): String? {
        return resources?.getString(R.string.source_migration)
    }

    override fun onItemClick(view: View?, position: Int): Boolean {
        val item = adapter?.getItem(position) as? SourceItem ?: return false
        val controller = MigrationMangaController(item.source.id, item.source.name)
        val parentController = parentController
        if (parentController is BrowseController) {
            parentController.router.pushController(controller.withFadeTransaction())
        } else {
            router.pushController(controller.withFadeTransaction())
        }

        return false
    }

    override fun onAllClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return

        launchUI {
            val manga = Injekt.get<DatabaseHelper>().getFavoriteMangas().executeOnIO()
            val sourceMangas = manga.asSequence().filter { it.source == item.source.id }.mapNotNull { it.id }.toList()
            withUIContext {
                PreMigrationController.navigateToMigration(
                    Injekt.get<PreferencesHelper>().skipPreMigration().get(),
                    run {
                        val parentController = parentController
                        if (parentController is BrowseController) {
                            parentController.router
                        } else {
                            router
                        }
                    },
                    sourceMangas
                )
            }
        }
    }
    // SY <--

    companion object {
        private const val HELP_URL = "https://tachiyomi.org/help/guides/source-migration/"
    }
}
