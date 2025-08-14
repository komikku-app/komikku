package eu.kanade.presentation.manga.components

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DownloadDropdownMenu
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MangaToolbar(
    title: String,
    // KMK -->
    incognitoMode: Boolean?,
    // KMK <--
    hasFilters: Boolean,
    navigateUp: () -> Unit,
    // KMK -->
    onToggleMangaIncognito: (() -> Unit)?,
    // KMK <--
    onClickFilter: () -> Unit,
    onClickShare: (() -> Unit)?,
    onClickDownload: ((DownloadAction) -> Unit)?,
    onClickEditCategory: (() -> Unit)?,
    onClickRefresh: () -> Unit,
    onClickMigrate: (() -> Unit)?,
    onClickEditNotes: () -> Unit,
    // SY -->
    onClickEditInfo: (() -> Unit)?,
    // KMK -->
    onClickRelatedMangas: (() -> Unit)?,
    onClickSourceSettings: (() -> Unit)?,
    onClearManga: () -> Unit,
    onOpenMangaFolder: (() -> Unit)?,
    // KMK <--
    onClickRecommend: (() -> Unit)?,
    onClickMerge: (() -> Unit)?,
    onClickMergedSettings: (() -> Unit)?,
    // SY <--

    // For action mode
    actionModeCounter: Int,
    onCancelActionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,

    titleAlphaProvider: () -> Float,
    backgroundAlphaProvider: () -> Float,
    // KMK -->
    onPaletteScreenClick: () -> Unit,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    // KMK -->
    val navigator = LocalNavigator.current
    fun onHomeClicked() = navigator?.popUntil { screen ->
        screen is SourceFeedScreen || screen is BrowseSourceScreen
    }
    val isHomeEnabled = Injekt.get<UiPreferences>().showHomeOnRelatedMangas().get()
    // KMK <--

    val isActionMode = actionModeCounter > 0
    AppBar(
        titleContent = {
            if (isActionMode) {
                AppBarTitle(actionModeCounter.toString())
            } else {
                AppBarTitle(title, modifier = Modifier.alpha(titleAlphaProvider()))
            }
        },
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme
            .surfaceColorAtElevation(3.dp)
            .copy(alpha = if (isActionMode) 1f else backgroundAlphaProvider()),
        // KMK -->
        goHome = { onHomeClicked() }.takeIf {
            isHomeEnabled &&
                navigator != null &&
                (
                    navigator.size >= 2 &&
                        navigator.items[navigator.size - 2] is MangaScreen ||
                        navigator.size >= 5
                    )
        },
        // KMK <--
        navigateUp = navigateUp,
        actions = {
            var downloadExpanded by remember { mutableStateOf(false) }
            if (onClickDownload != null) {
                val onDismissRequest = { downloadExpanded = false }
                DownloadDropdownMenu(
                    expanded = downloadExpanded,
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onClickDownload,
                )
            }

            val filterTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current
            AppBarActions(
                actions = persistentListOf<AppBar.AppBarAction>().builder().apply {
                    if (isActionMode) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_all),
                                icon = Icons.Outlined.SelectAll,
                                onClick = onSelectAll,
                            ),
                        )
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_select_inverse),
                                icon = Icons.Outlined.FlipToBack,
                                onClick = onInvertSelection,
                            ),
                        )
                        return@apply
                    }
                    if (onClickDownload != null) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.manga_download),
                                icon = Icons.Outlined.Download,
                                onClick = { downloadExpanded = !downloadExpanded },
                            ),
                        )
                    }
                    // KMK -->
                    if (onToggleMangaIncognito != null) {
                        add(
                            AppBar.Action(
                                title = stringResource(MR.strings.pref_incognito_mode),
                                icon = null,
                                iconPainter = rememberAnimatedVectorPainter(
                                    AnimatedImageVector.animatedVectorResource(R.drawable.anim_incognito),
                                    incognitoMode == true,
                                ),
                                iconTint = if (incognitoMode == true) MaterialTheme.colorScheme.primary else null,
                                onClick = onToggleMangaIncognito,
                            ),
                        )
                    }
                    // KMK <--
                    add(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_filter),
                            icon = Icons.Outlined.FilterList,
                            iconTint = filterTint,
                            onClick = onClickFilter,
                        ),
                    )
                    add(
                        AppBar.OverflowAction(
                            title = stringResource(MR.strings.action_webview_refresh),
                            onClick = onClickRefresh,
                        ),
                    )
                    if (onClickEditCategory != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_edit_categories),
                                onClick = onClickEditCategory,
                            ),
                        )
                    }
                    if (onClickMigrate != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_migrate),
                                onClick = onClickMigrate,
                            ),
                        )
                    }
                    if (onClickShare != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_share),
                                onClick = onClickShare,
                            ),
                        )
                    }
                    add(
                        AppBar.OverflowAction(
                            title = stringResource(MR.strings.action_notes),
                            onClick = onClickEditNotes,
                        ),
                    )
                    // SY -->
                    if (onClickMerge != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(SYMR.strings.merge),
                                onClick = onClickMerge,
                            ),
                        )
                    }
                    if (onClickEditInfo != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(SYMR.strings.action_edit_info),
                                onClick = onClickEditInfo,
                            ),
                        )
                    }
                    // KMK -->
                    if (onClickRelatedMangas != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(KMR.strings.pref_source_related_mangas),
                                onClick = onClickRelatedMangas,
                            ),
                        )
                    }
                    // KMK <--
                    if (onClickRecommend != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(SYMR.strings.az_recommends),
                                onClick = onClickRecommend,
                            ),
                        )
                    }
                    if (onClickMergedSettings != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(SYMR.strings.merge_settings),
                                onClick = onClickMergedSettings,
                            ),
                        )
                    }
                    // SY <--
                    // KMK -->
                    if (onOpenMangaFolder != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(KMR.strings.action_open_folder),
                                onClick = onOpenMangaFolder,
                            ),
                        )
                    }
                    add(
                        AppBar.OverflowAction(
                            title = stringResource(KMR.strings.action_clear_manga),
                            onClick = onClearManga,
                        ),
                    )
                    if (onClickSourceSettings != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.source_settings),
                                onClick = onClickSourceSettings,
                            ),
                        )
                    }
                    if (isDebugBuildType) {
                        add(
                            AppBar.OverflowAction(
                                title = "Colors Palette",
                                onClick = onPaletteScreenClick,
                            ),
                        )
                    }
                    // KMK <--
                }
                    .build(),
            )
        },
        isActionMode = isActionMode,
        onCancelActionMode = onCancelActionMode,
    )
}
