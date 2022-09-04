package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.MigrationListControllerBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.changehandler.OneWayFadeChangeHandler
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.migration.MigrationMangaDialog
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga.SearchResult
import eu.kanade.tachiyomi.ui.browse.migration.search.SearchController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.getParcelableCompat
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MigrationListController(bundle: Bundle? = null) :
    NucleusController<MigrationListControllerBinding, MigrationListPresenter>(bundle) {

    constructor(config: MigrationProcedureConfig) : this(
        bundleOf(
            CONFIG_EXTRA to config,
        ),
    )

    init {
        setHasOptionsMenu(true)
    }

    private var adapter: MigrationProcessAdapter? = null

    val config = args.getParcelableCompat<MigrationProcedureConfig>(CONFIG_EXTRA)

    private var selectedMangaId: Long? = null
    private var manualMigrations = 0

    override fun getTitle(): String {
        val notFinished = presenter.migratingItems.value.count {
            it.searchResult.value != SearchResult.Searching
        }
        val total = presenter.migratingItems.value.size
        return activity?.getString(R.string.migration) + " ($notFinished/$total)"
    }

    override fun createPresenter(): MigrationListPresenter {
        return MigrationListPresenter(config!!)
    }

    override fun createBinding(inflater: LayoutInflater) = MigrationListControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        setTitle()

        adapter = MigrationProcessAdapter(this)

        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)

        presenter.migratingItems
            .onEach {
                adapter?.updateDataSet(it.map { it.toModal() })
            }
            .launchIn(viewScope)
    }

    fun updateCount() {
        if (router.backstack.lastOrNull()?.controller == this@MigrationListController) {
            setTitle()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun enableButtons() {
        activity?.invalidateOptionsMenu()
    }

    private fun noMigration() {
        val res = resources
        if (res != null) {
            activity?.toast(
                res.getQuantityString(
                    R.plurals.manga_migrated,
                    manualMigrations,
                    manualMigrations,
                ),
            )
        }
        if (!presenter.hideNotFound) {
            router.popCurrentController()
        }
    }

    fun onMenuItemClick(mangaId: Long, item: MenuItem) {
        when (item.itemId) {
            R.id.action_search_manually -> {
                val manga = presenter.migratingItems.value
                    .find { it.manga.id == mangaId }
                    ?.manga
                    ?: return
                selectedMangaId = mangaId
                val sources = presenter.getMigrationSources()
                val validSources = if (sources.size == 1) {
                    sources
                } else {
                    sources.filter { it.id != manga.source }
                }
                val searchController = SearchController(manga, validSources)
                searchController.targetController = this@MigrationListController
                router.pushController(searchController)
            }
            R.id.action_skip -> presenter.removeManga(mangaId)
            R.id.action_migrate_now -> {
                migrateManga(mangaId, false)
                manualMigrations++
            }
            R.id.action_copy_now -> {
                migrateManga(mangaId, true)
                manualMigrations++
            }
        }
    }

    fun useMangaForMigration(manga: Manga, source: Source) {
        presenter.useMangaForMigration(manga, source, selectedMangaId ?: return)
    }

    fun migrateMangas() {
        presenter.migrateMangas()
    }

    fun copyMangas() {
        presenter.copyMangas()
    }

    fun migrateManga(mangaId: Long, copy: Boolean) {
        presenter.migrateManga(mangaId, copy)
    }

    fun removeManga(mangaId: Long) {
        presenter.removeManga(mangaId)
    }

    fun sourceFinished() {
        updateCount()
        if (presenter.migratingItems.value.isEmpty()) noMigration()
        if (presenter.allMangasDone()) enableButtons()
    }

    fun navigateOut(manga: Manga?) {
        if (manga != null) {
            val newStack = router.backstack.filter {
                it.controller !is MangaController &&
                    it.controller !is MigrationListController &&
                    it.controller !is PreMigrationController
            } + MangaController(manga.id).withFadeTransaction()
            router.setBackstack(newStack, OneWayFadeChangeHandler())
            return
        }
        router.popCurrentController()
    }

    override fun handleBack(): Boolean {
        activity?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.stop_migrating)
                .setPositiveButton(R.string.action_stop) { _, _ ->
                    router.popCurrentController()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.migration_list, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        // Initialize menu items.

        val allMangasDone = presenter.allMangasDone()

        val menuCopy = menu.findItem(R.id.action_copy_manga)
        val menuMigrate = menu.findItem(R.id.action_migrate_manga)

        if (presenter.migratingItems.value.size == 1) {
            menuMigrate.icon = VectorDrawableCompat.create(
                resources!!,
                R.drawable.ic_done_24dp,
                null,
            )
        }

        val tintColor = activity?.getResourceColor(R.attr.colorOnSurface) ?: Color.WHITE
        val color = if (allMangasDone) {
            tintColor
        } else {
            ColorUtils.setAlphaComponent(tintColor, 127)
        }
        menuCopy.setIconTint(allMangasDone, color)
        menuMigrate.setIconTint(allMangasDone, color)
    }

    private fun MenuItem.setIconTint(enabled: Boolean, color: Int) {
        icon?.mutate()
        icon?.setTint(color)
        isEnabled = enabled
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val totalManga = presenter.migratingItems.value.size
        val mangaSkipped = presenter.mangasSkipped()
        when (item.itemId) {
            R.id.action_copy_manga -> MigrationMangaDialog(
                this,
                true,
                totalManga,
                mangaSkipped,
            ).showDialog(router)
            R.id.action_migrate_manga -> MigrationMangaDialog(
                this,
                false,
                totalManga,
                mangaSkipped,
            ).showDialog(router)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        const val CONFIG_EXTRA = "config_extra"
        const val TAG = "migration_list"
    }
}
