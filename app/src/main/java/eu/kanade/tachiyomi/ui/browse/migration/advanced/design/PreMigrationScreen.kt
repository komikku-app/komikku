package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.SOURCE_SEARCH_BOX_HEIGHT
import eu.kanade.presentation.components.SourcesSearchBox
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.databinding.PreMigrationListBinding
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListScreen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationProcedureConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.io.Serializable
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

sealed class MigrationType : Serializable {
    data class MangaList(val mangaIds: List<Long>) : MigrationType()
    data class MangaSingle(val fromMangaId: Long, val toManga: Long?) : MigrationType()
}

/**
 * The screen showing a list of selectable sources used for migration, with migration setting dialog.
 */
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
                )
            },
            // KMK -->
            bottomBar = {
                PreMigrationScreenBottomBar(
                    modifier = Modifier,
                    onSelectAll = { screenModel.massSelect(true) },
                    onSelectNone = { screenModel.massSelect(false) },
                    onSelectPinned = { screenModel.matchSelection(false) },
                    onSelectEnabled = { screenModel.matchSelection(true) },
                )
            },
            // KMK <--
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
            val density = LocalDensity.current
            Box(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
                var searchBoxHeight by remember { mutableIntStateOf(with(density) { SOURCE_SEARCH_BOX_HEIGHT.roundToPx() }) }
                // KMK <--
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
                SourcesSearchBox(
                    searchQuery = searchQuery,
                    onChangeSearchQuery = { searchQuery = it ?: "" },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = MaterialTheme.padding.small),
                    onGloballyPositioned = { layoutCoordinates ->
                        searchBoxHeight = layoutCoordinates.size.height
                    },
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

    // KMK -->
    @Composable
    fun PreMigrationScreenBottomBar(
        modifier: Modifier,
        onSelectAll: () -> Unit,
        onSelectNone: () -> Unit,
        onSelectPinned: () -> Unit,
        onSelectEnabled: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = ZeroCornerSize,
                bottomStart = ZeroCornerSize,
            ),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(elevation = 0.dp),
        ) {
            val haptic = LocalHapticFeedback.current
            val confirm = remember { mutableStateListOf(false, false, false, false) }
            var resetJob: Job? = remember { null }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                (0 until 4).forEach { i -> confirm[i] = i == toConfirmIndex }
                resetJob?.cancel()
                resetJob = scope.launch {
                    delay(1.seconds)
                    if (isActive) confirm[toConfirmIndex] = false
                }
            }
            Row(
                modifier = Modifier
                    .padding(
                        WindowInsets.navigationBars
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues(),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Button(
                    title = stringResource(MR.strings.action_select_all),
                    icon = Icons.Outlined.SelectAll,
                    onClick = onSelectAll,
                    toConfirm = confirm[0],
                    onLongClick = { onLongClickItem(0) },
                )
                Button(
                    title = stringResource(SYMR.strings.select_none),
                    icon = Icons.Outlined.Deselect,
                    onClick = onSelectNone,
                    toConfirm = confirm[1],
                    onLongClick = { onLongClickItem(1) },
                )
                Button(
                    title = stringResource(SYMR.strings.match_enabled_sources),
                    icon = Icons.Outlined.ToggleOn,
                    onClick = onSelectEnabled,
                    toConfirm = confirm[2],
                    onLongClick = { onLongClickItem(2) },
                )
                Button(
                    title = stringResource(SYMR.strings.match_pinned_sources),
                    icon = Icons.Outlined.PushPin,
                    onClick = onSelectPinned,
                    toConfirm = confirm[3],
                    onLongClick = { onLongClickItem(3) },
                )
            }
        }
    }

    @Composable
    private fun RowScope.Button(
        title: String,
        icon: ImageVector,
        toConfirm: Boolean,
        onLongClick: () -> Unit,
        onClick: (() -> Unit),
        content: (@Composable () -> Unit)? = null,
    ) {
        val animatedWeight by animateFloatAsState(
            targetValue = if (toConfirm) 2f else 1f,
            label = "weight",
        )
        Column(
            modifier = Modifier
                .size(48.dp)
                .weight(animatedWeight)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
            )
            AnimatedVisibility(
                visible = toConfirm,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Text(
                    text = title,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            content?.invoke()
        }
    }
    // KMK <--

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

        /* All usages have been replaced by original Mihon's migration dialog */
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
