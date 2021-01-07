package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.Router
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.PreMigrationControllerBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationProcedureConfig
import eu.kanade.tachiyomi.util.view.shrinkOnScroll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import uy.kohesive.injekt.injectLazy

class PreMigrationController(bundle: Bundle? = null) :
    BaseController<PreMigrationControllerBinding>(bundle),
    FlexibleAdapter.OnItemClickListener,
    FabController,
    StartMigrationListener {
    private val sourceManager: SourceManager by injectLazy()
    private val prefs: PreferencesHelper by injectLazy()

    private var adapter: MigrationSourceAdapter? = null

    private val config: LongArray = args.getLongArray(MANGA_IDS_EXTRA) ?: LongArray(0)

    val scope = CoroutineScope(Job() + Dispatchers.Main)

    private var actionFab: ExtendedFloatingActionButton? = null
    private var actionFabScrollListener: RecyclerView.OnScrollListener? = null

    private var dialog: BottomSheetDialog? = null

    override fun getTitle() = view?.context?.getString(R.string.select_sources)

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = PreMigrationControllerBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        val ourAdapter = adapter ?: MigrationSourceAdapter(
            getEnabledSources().map { MigrationSourceItem(it, isEnabled(it.id.toString())) },
            this
        )
        adapter = ourAdapter
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.adapter = ourAdapter
        ourAdapter.itemTouchHelperCallback = null // Reset adapter touch adapter to fix drag after rotation
        ourAdapter.isHandleDragEnabled = true
        dialog = null

        actionFabScrollListener = actionFab?.shrinkOnScroll(binding.recycler)
    }

    override fun configureFab(fab: ExtendedFloatingActionButton) {
        actionFab = fab
        fab.setText(R.string.action_migrate)
        fab.setIconResource(R.drawable.ic_arrow_forward_24dp)
        fab.setOnClickListener {
            if (dialog?.isShowing != true) {
                dialog = MigrationBottomSheetDialog(activity!!, R.style.SheetDialog, this)
                dialog?.show()
                val bottomSheet = dialog?.findViewById<FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet
                )
                if (bottomSheet != null) {
                    val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(bottomSheet)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }
            }
        }
    }

    override fun cleanupFab(fab: ExtendedFloatingActionButton) {
        fab.setOnClickListener(null)
        actionFabScrollListener?.let { binding.recycler.removeOnScrollListener(it) }
        actionFab = null
    }

    override fun startMigration(extraParam: String?) {
        val listOfSources = adapter?.items?.filter {
            it.sourceEnabled
        }?.joinToString("/") { it.source.id.toString() }.orEmpty()
        prefs.migrationSources().set(listOfSources)

        router.replaceTopController(
            MigrationListController.create(
                MigrationProcedureConfig(
                    config.toList(),
                    extraSearchParams = extraParam
                )
            ).withFadeTransaction().tag(MigrationListController.TAG)
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        adapter?.onSaveInstanceState(outState)
    }

    // TODO Still incorrect, why is this called before onViewCreated?
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        adapter?.onRestoreInstanceState(savedInstanceState)
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        adapter?.getItem(position)?.let {
            it.sourceEnabled = !it.sourceEnabled
        }
        adapter?.notifyItemChanged(position)
        return false
    }

    /**
     * Returns a list of enabled sources ordered by language and name.
     *
     * @return list containing enabled sources.
     */
    private fun getEnabledSources(): List<HttpSource> {
        val languages = prefs.enabledLanguages().get()
        val sourcesSaved = prefs.migrationSources().get().split("/")
        val sources = sourceManager.getVisibleCatalogueSources()
            .filterIsInstance<HttpSource>()
            .filter { it.lang in languages }
            .sortedBy { "(${it.lang}) ${it.name}" }

        return sources.filter { isEnabled(it.id.toString()) }.sortedBy { sourcesSaved.indexOf(it.id.toString()) } + sources.filterNot { isEnabled(it.id.toString()) }
    }

    fun isEnabled(id: String): Boolean {
        val sourcesSaved = prefs.migrationSources().get()
        val disabledSourceIds = prefs.disabledSources().get()
        return if (sourcesSaved.isEmpty()) id !in disabledSourceIds
        else sourcesSaved.split("/").contains(id)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.pre_migration, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_select_all, R.id.action_select_none -> {
                adapter?.currentItems?.forEach {
                    it.sourceEnabled = item.itemId == R.id.action_select_all
                }
                adapter?.notifyDataSetChanged()
            }
            R.id.action_match_enabled, R.id.action_match_pinned -> {
                val enabledSources = if (item.itemId == R.id.action_match_enabled) {
                    prefs.disabledSources().get().mapNotNull { it.toLongOrNull() }
                } else {
                    prefs.pinnedSources().get().mapNotNull { it.toLongOrNull() }
                }
                val items = adapter?.currentItems?.toList() ?: return true
                items.forEach {
                    it.sourceEnabled = if (item.itemId == R.id.action_match_enabled) {
                        it.source.id !in enabledSources
                    } else {
                        it.source.id in enabledSources
                    }
                }
                val sortedItems = items.sortedBy { it.source.name }.sortedBy { !it.sourceEnabled }
                adapter?.updateDataSet(sortedItems)
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        private const val MANGA_IDS_EXTRA = "manga_ids"

        fun navigateToMigration(skipPre: Boolean, router: Router, mangaIds: List<Long>) {
            router.pushController(
                if (skipPre) {
                    MigrationListController.create(
                        MigrationProcedureConfig(mangaIds, null)
                    )
                } else {
                    create(mangaIds)
                }.withFadeTransaction().tag(if (skipPre) MigrationListController.TAG else null)
            )
        }

        fun create(mangaIds: List<Long>): PreMigrationController {
            return PreMigrationController(
                bundleOf(
                    MANGA_IDS_EXTRA to mangaIds.toLongArray()
                )
            )
        }
    }
}
