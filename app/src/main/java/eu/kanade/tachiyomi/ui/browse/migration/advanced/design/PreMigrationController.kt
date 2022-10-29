package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.bluelinelabs.conductor.Router
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.ExtendedFloatingActionButton
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.PreMigrationListBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationProcedureConfig
import eu.kanade.tachiyomi.util.lang.launchIO
import uy.kohesive.injekt.injectLazy
import kotlin.math.roundToInt

class PreMigrationController(bundle: Bundle? = null) :
    BasicFullComposeController(bundle),
    FlexibleAdapter.OnItemClickListener,
    StartMigrationListener {

    constructor(mangaIds: List<Long>) : this(
        bundleOf(
            MANGA_IDS_EXTRA to mangaIds.toLongArray(),
        ),
    )

    private val sourceManager: SourceManager by injectLazy()
    private val prefs: UnsortedPreferences by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()

    private var adapter: MigrationSourceAdapter? = null

    private val config: LongArray = args.getLongArray(MANGA_IDS_EXTRA) ?: LongArray(0)

    private lateinit var dialog: MigrationBottomSheetDialog

    private lateinit var controllerBinding: PreMigrationListBinding

    var items by mutableStateOf(emptyList<MigrationSourceItem>())

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        dialog = MigrationBottomSheetDialog(activity!!, this)

        viewScope.launchIO {
            items = getEnabledSources()
        }
    }

    @Composable
    override fun ComposeContent() {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        var fabExpanded by remember { mutableStateOf(true) }
        val nestedScrollConnection = remember {
            // All this lines just for fab state :/
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    fabExpanded = available.y >= 0
                    return scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    return scrollBehavior.nestedScrollConnection.onPostScroll(consumed, available, source)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    return scrollBehavior.nestedScrollConnection.onPreFling(available)
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    return scrollBehavior.nestedScrollConnection.onPostFling(consumed, available)
                }
            }
        }
        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(R.string.select_sources),
                    navigateUp = router::popCurrentController,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { massSelect(false) }) {
                            Icon(
                                imageVector = Icons.Outlined.Deselect,
                                contentDescription = stringResource(R.string.select_none),
                            )
                        }
                        IconButton(onClick = { massSelect(true) }) {
                            Icon(
                                imageVector = Icons.Outlined.SelectAll,
                                contentDescription = stringResource(R.string.action_select_all),
                            )
                        }
                        val (expanded, onExpanded) = remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { onExpanded(!expanded) }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.label_more),
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { onExpanded(false) },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.match_enabled_sources)) },
                                    onClick = { matchSelection(true) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.match_pinned_sources)) },
                                    onClick = { matchSelection(false) },
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(R.string.action_migrate)) },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.ArrowForward,
                            contentDescription = stringResource(R.string.action_migrate),
                        )
                    },
                    onClick = {
                        if (!dialog.isShowing) {
                            dialog.show()
                        }
                    },
                    expanded = fabExpanded,
                    modifier = Modifier.navigationBarsPadding(),
                )
            },
        ) { contentPadding ->
            val density = LocalDensity.current
            val layoutDirection = LocalLayoutDirection.current
            val left = with(density) { contentPadding.calculateLeftPadding(layoutDirection).toPx().roundToInt() }
            val top = with(density) { contentPadding.calculateTopPadding().toPx().roundToInt() }
            val right = with(density) { contentPadding.calculateRightPadding(layoutDirection).toPx().roundToInt() }
            val bottom = with(density) { contentPadding.calculateBottomPadding().toPx().roundToInt() }
            Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
                AndroidView(
                    factory = { context ->
                        controllerBinding = PreMigrationListBinding.inflate(LayoutInflater.from(context))
                        adapter = MigrationSourceAdapter(this@PreMigrationController)
                        controllerBinding.recycler.adapter = adapter
                        adapter?.isHandleDragEnabled = true
                        adapter?.fastScroller = controllerBinding.fastScroller
                        controllerBinding.recycler.layoutManager = LinearLayoutManager(context)

                        ViewCompat.setNestedScrollingEnabled(controllerBinding.root, true)

                        controllerBinding.root
                    },
                    update = {
                        controllerBinding.recycler
                            .updatePadding(
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom,
                            )

                        controllerBinding.fastScroller
                            .updateLayoutParams<ViewGroup.MarginLayoutParams> {
                                leftMargin = left
                                topMargin = top
                                rightMargin = right
                                bottomMargin = bottom
                            }

                        adapter?.updateDataSet(items)
                    },
                )
            }
        }
    }

    override fun startMigration(extraParam: String?) {
        val listOfSources = adapter?.currentItems
            ?.filterIsInstance<MigrationSourceItem>()
            ?.filter {
                it.sourceEnabled
            }
            ?.joinToString("/") { it.source.id.toString() }
            .orEmpty()

        prefs.migrationSources().set(listOfSources)

        router.replaceTopController(
            MigrationListController(
                MigrationProcedureConfig(
                    config.toList(),
                    extraSearchParams = extraParam,
                ),
            ).withFadeTransaction().tag(MigrationListController.TAG),
        )
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
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
        val adapter = adapter ?: return false
        adapter.getItem(position)?.let {
            it.sourceEnabled = !it.sourceEnabled
        }
        adapter.notifyItemChanged(position)
        return false
    }

    /**
     * Returns a list of enabled sources ordered by language and name.
     *
     * @return list containing enabled sources.
     */
    private fun getEnabledSources(): List<MigrationSourceItem> {
        val languages = sourcePreferences.enabledLanguages().get()
        val sourcesSaved = prefs.migrationSources().get().split("/")
            .mapNotNull { it.toLongOrNull() }
        val disabledSources = sourcePreferences.disabledSources().get()
            .mapNotNull { it.toLongOrNull() }
        val sources = sourceManager.getVisibleCatalogueSources()
            .asSequence()
            .filterIsInstance<HttpSource>()
            .filter { it.lang in languages }
            .sortedBy { "(${it.lang}) ${it.name}" }
            .map {
                MigrationSourceItem(
                    it,
                    isEnabled(
                        sourcesSaved,
                        disabledSources,
                        it.id,
                    ),
                )
            }
            .toList()

        return sources
            .filter { it.sourceEnabled }
            .sortedBy { sourcesSaved.indexOf(it.source.id) }
            .plus(
                sources.filterNot { it.sourceEnabled },
            )
    }

    fun isEnabled(
        sourcesSaved: List<Long>,
        disabledSources: List<Long>,
        id: Long,
    ): Boolean {
        return if (sourcesSaved.isEmpty()) {
            id !in disabledSources
        } else {
            id in sourcesSaved
        }
    }

    private fun massSelect(selectAll: Boolean) {
        val adapter = adapter ?: return
        adapter.currentItems.forEach {
            it.sourceEnabled = selectAll
        }
        adapter.notifyDataSetChanged()
    }

    private fun matchSelection(matchEnabled: Boolean) {
        val adapter = adapter ?: return
        val enabledSources = if (matchEnabled) {
            sourcePreferences.disabledSources().get().mapNotNull { it.toLongOrNull() }
        } else {
            sourcePreferences.pinnedSources().get().mapNotNull { it.toLongOrNull() }
        }
        val items = adapter.currentItems.toList()
        items.forEach {
            it.sourceEnabled = if (matchEnabled) {
                it.source.id !in enabledSources
            } else {
                it.source.id in enabledSources
            }
        }
        val sortedItems = items.sortedBy { it.source.name }.sortedBy { !it.sourceEnabled }
        adapter.updateDataSet(sortedItems)
    }

    companion object {
        private const val MANGA_IDS_EXTRA = "manga_ids"

        fun navigateToMigration(skipPre: Boolean, router: Router, mangaIds: List<Long>) {
            router.pushController(
                if (skipPre) {
                    MigrationListController(
                        MigrationProcedureConfig(mangaIds, null),
                    )
                } else {
                    PreMigrationController(mangaIds)
                }.withFadeTransaction().tag(if (skipPre) MigrationListController.TAG else null),
            )
        }
    }
}
