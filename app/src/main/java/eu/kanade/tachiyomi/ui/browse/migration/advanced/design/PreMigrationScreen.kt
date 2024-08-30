package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.SourcesSearch
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.databinding.PreMigrationListBinding
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListScreen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationProcedureConfig
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.io.Serializable
import kotlin.math.roundToInt

sealed class MigrationType : Serializable {
    data class MangaList(val mangaIds: List<Long>) : MigrationType()
    data class MangaSingle(val fromMangaId: Long, val toManga: Long?) : MigrationType()
}

class PreMigrationScreen(val migration: MigrationType) : Screen() {
    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { PreMigrationScreenModel() }
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        val navigator = LocalNavigator.currentOrThrow
        var fabExpanded by remember { mutableStateOf(true) }
        val items by screenModel.state.collectAsState()
        val adapter by screenModel.adapter.collectAsState()
        // KMK -->
        var searchQuery by remember { mutableStateOf("") }
        BackHandler(enabled = searchQuery.isNotBlank()) {
            searchQuery = ""
        }
        // KMK <--
        LaunchedEffect(items.isNotEmpty(), adapter != null/* KMK --> */, searchQuery/* KMK <-- */) {
            if (adapter != null && items.isNotEmpty()) {
                adapter?.updateDataSet(
                    items
                        // KMK -->
                        .filter { migrationSource ->
                            if (searchQuery.isBlank()) return@filter true
                            val source = migrationSource.source
                            searchQuery.split(",").any {
                                val input = it.trim()
                                if (input.isEmpty()) return@any false
                                source.name.contains(input, ignoreCase = true) ||
                                    source.id == input.toLongOrNull()
                            }
                        },
                    // KMK <--
                )
            }
        }

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
                    title = stringResource(SYMR.strings.select_sources),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(SYMR.strings.select_none),
                                    icon = Icons.Outlined.Deselect,
                                    onClick = { screenModel.massSelect(false) },
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_select_all),
                                    icon = Icons.Outlined.SelectAll,
                                    onClick = { screenModel.massSelect(true) },
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(SYMR.strings.match_enabled_sources),
                                    onClick = { screenModel.matchSelection(true) },
                                ),
                                AppBar.OverflowAction(
                                    title = stringResource(SYMR.strings.match_pinned_sources),
                                    onClick = { screenModel.matchSelection(false) },
                                ),
                            ),
                        )
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text(text = stringResource(MR.strings.action_migrate)) },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = stringResource(MR.strings.action_migrate),
                        )
                    },
                    onClick = {
                        screenModel.onMigrationSheet(true)
                    },
                    expanded = fabExpanded,
                )
            },
        ) { contentPadding ->
            // KMK -->
            Box(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
                var searchBoxHeight by remember { mutableIntStateOf(40) }
                // KMK <--
                val density = LocalDensity.current
                val layoutDirection = LocalLayoutDirection.current
                val left = with(density) { contentPadding.calculateLeftPadding(layoutDirection).toPx().roundToInt() }
                // KMK -->
                val top = searchBoxHeight
                // val top = with(density) { contentPadding.calculateTopPadding().toPx().roundToInt() }
                // KMK <--
                val right = with(density) { contentPadding.calculateRightPadding(layoutDirection).toPx().roundToInt() }
                val bottom = with(density) { contentPadding.calculateBottomPadding().toPx().roundToInt() }
                Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context ->
                            screenModel.controllerBinding =
                                PreMigrationListBinding.inflate(LayoutInflater.from(context))
                            screenModel.adapter.value = MigrationSourceAdapter(screenModel.clickListener)
                            screenModel.controllerBinding.root.adapter = screenModel.adapter.value
                            screenModel.adapter.value?.isHandleDragEnabled = true
                            screenModel.controllerBinding.root.layoutManager = LinearLayoutManager(context)

                            ViewCompat.setNestedScrollingEnabled(screenModel.controllerBinding.root, true)

                            screenModel.controllerBinding.root
                        },
                        update = {
                            screenModel.controllerBinding.root
                                .updatePadding(
                                    left = left,
                                    top = top,
                                    right = right,
                                    bottom = bottom,
                                )
                        },
                    )
                }
                // KMK -->
                SourcesSearch(
                    modifier = Modifier
                        .onSizeChanged { searchBoxHeight = it.height }
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = MaterialTheme.padding.small),
                    searchQuery = searchQuery,
                    onChangeSearchQuery = { searchQuery = it ?: "" },
                    placeholderText = stringResource(KMR.strings.action_search_for_source),
                )
                // KMK <--
            }
        }

        val migrationSheetOpen by screenModel.migrationSheetOpen.collectAsState()
        if (migrationSheetOpen) {
            MigrationBottomSheetDialog(
                onDismissRequest = { screenModel.onMigrationSheet(false) },
                onStartMigration = { extraParam ->
                    screenModel.onMigrationSheet(false)
                    screenModel.saveEnabledSources()

                    navigator replace MigrationListScreen(MigrationProcedureConfig(migration, extraParam))
                },
            )
        }
    }

    companion object {
        fun navigateToMigration(skipPre: Boolean, navigator: Navigator, mangaIds: List<Long>) {
            navigator.push(
                if (skipPre) {
                    MigrationListScreen(
                        MigrationProcedureConfig(MigrationType.MangaList(mangaIds), null),
                    )
                } else {
                    PreMigrationScreen(MigrationType.MangaList(mangaIds))
                },
            )
        }

        fun navigateToMigration(skipPre: Boolean, navigator: Navigator, fromMangaId: Long, toManga: Long?) {
            navigator.push(
                if (skipPre) {
                    MigrationListScreen(
                        MigrationProcedureConfig(MigrationType.MangaSingle(fromMangaId, toManga), null),
                    )
                } else {
                    PreMigrationScreen(MigrationType.MangaSingle(fromMangaId, toManga))
                },
            )
        }
    }
}
