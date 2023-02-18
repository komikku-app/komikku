package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.OverflowMenu
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.PreMigrationListBinding
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListScreen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationProcedureConfig
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import kotlin.math.roundToInt

class PreMigrationScreen(val mangaIds: List<Long>) : Screen {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { PreMigrationScreenModel() }
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        val navigator = LocalNavigator.currentOrThrow
        var fabExpanded by remember { mutableStateOf(true) }
        val items by screenModel.state.collectAsState()
        val context = LocalContext.current
        DisposableEffect(screenModel) {
            screenModel.dialog = MigrationBottomSheetDialog(context, screenModel.listener)
            onDispose {}
        }

        LaunchedEffect(screenModel) {
            screenModel.startMigration.collect { extraParam ->
                navigator replace MigrationListScreen(MigrationProcedureConfig(mangaIds, extraParam))
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
                    title = stringResource(R.string.select_sources),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { screenModel.massSelect(false) }) {
                            Icon(
                                imageVector = Icons.Outlined.Deselect,
                                contentDescription = stringResource(R.string.select_none),
                            )
                        }
                        IconButton(onClick = { screenModel.massSelect(true) }) {
                            Icon(
                                imageVector = Icons.Outlined.SelectAll,
                                contentDescription = stringResource(R.string.action_select_all),
                            )
                        }
                        OverflowMenu { closeMenu ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.match_enabled_sources)) },
                                onClick = {
                                    screenModel.matchSelection(true)
                                    closeMenu()
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.match_pinned_sources)) },
                                onClick = {
                                    screenModel.matchSelection(false)
                                    closeMenu()
                                },
                            )
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
                        if (!screenModel.dialog.isShowing) {
                            screenModel.dialog.show()
                        }
                    },
                    expanded = fabExpanded,
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
                        screenModel.controllerBinding = PreMigrationListBinding.inflate(LayoutInflater.from(context))
                        screenModel.adapter = MigrationSourceAdapter(screenModel.clickListener)
                        screenModel.controllerBinding.recycler.adapter = screenModel.adapter
                        screenModel.adapter?.isHandleDragEnabled = true
                        screenModel.adapter?.fastScroller = screenModel.controllerBinding.fastScroller
                        screenModel.controllerBinding.recycler.layoutManager = LinearLayoutManager(context)

                        ViewCompat.setNestedScrollingEnabled(screenModel.controllerBinding.root, true)

                        screenModel.controllerBinding.root
                    },
                    update = {
                        screenModel.controllerBinding.recycler
                            .updatePadding(
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom,
                            )

                        screenModel.controllerBinding.fastScroller
                            .updateLayoutParams<ViewGroup.MarginLayoutParams> {
                                leftMargin = left
                                topMargin = top
                                rightMargin = right
                                bottomMargin = bottom
                            }

                        screenModel.adapter?.updateDataSet(items)
                    },
                )
            }
        }
    }

    companion object {
        fun navigateToMigration(skipPre: Boolean, navigator: Navigator, mangaIds: List<Long>) {
            navigator.push(
                if (skipPre) {
                    MigrationListScreen(
                        MigrationProcedureConfig(mangaIds, null),
                    )
                } else {
                    PreMigrationScreen(mangaIds)
                },
            )
        }
    }
}
