package eu.kanade.presentation.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.AnimatedFloatingSearchBox
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrateSourceScreenModel
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.source.EHENTAI_EXT_SOURCES
import exh.source.EXHENTAI_EXT_SOURCES
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MigrateSourceScreen(
    state: MigrateSourceScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source) -> Unit,
    onToggleSortingDirection: () -> Unit,
    onToggleSortingMode: () -> Unit,
    // KMK -->
    onChangeSearchQuery: (String?) -> Unit,
    // KMK <--
) {
    val context = LocalContext.current
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        // KMK -->
        state.searchQuery == null &&
            // KMK <--
            state.isEmpty -> EmptyScreen(
            stringRes = MR.strings.information_empty_library,
            modifier = Modifier.padding(contentPadding),
        )
        else ->
            MigrateSourceList(
                list = state.items,
                contentPadding = contentPadding,
                onClickItem = onClickItem,
                onLongClickItem = { source ->
                    val sourceId = source.id.toString()
                    context.copyToClipboard(sourceId, sourceId)
                },
                sortingMode = state.sortingMode,
                onToggleSortingMode = onToggleSortingMode,
                sortingDirection = state.sortingDirection,
                onToggleSortingDirection = onToggleSortingDirection,
                // KMK -->
                state = state,
                onChangeSearchQuery = onChangeSearchQuery,
                // KMK <--
            )
    }
}

@Composable
private fun MigrateSourceList(
    list: ImmutableList<Pair<Source, Long>>,
    contentPadding: PaddingValues,
    onClickItem: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    sortingMode: SetMigrateSorting.Mode,
    onToggleSortingMode: () -> Unit,
    sortingDirection: SetMigrateSorting.Direction,
    onToggleSortingDirection: () -> Unit,
    // KMK -->
    state: MigrateSourceScreenModel.State,
    onChangeSearchQuery: (String?) -> Unit,
    // KMK <--
) {
    // KMK -->
    val lazyListState = rememberLazyListState()
    var filterObsoleteSource by rememberSaveable { mutableStateOf(false) }
    val isHentaiEnabled = remember { Injekt.get<UnsortedPreferences>().isHentaiEnabled().get() }

    BackHandler(enabled = !state.searchQuery.isNullOrBlank()) {
        onChangeSearchQuery("")
    }

    Column(
        modifier = Modifier.padding(contentPadding),
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(start = MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.migration_selection_prompt),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.header,
            )

            // KMK -->
            IconButton(onClick = { filterObsoleteSource = !filterObsoleteSource }) {
                Icon(
                    Icons.Outlined.NewReleases,
                    contentDescription = stringResource(MR.strings.ext_obsolete),
                    tint = MaterialTheme.colorScheme.error
                        .takeIf { filterObsoleteSource } ?: LocalContentColor.current,
                )
            }
            // KMK <--
            IconButton(onClick = onToggleSortingMode) {
                when (sortingMode) {
                    SetMigrateSorting.Mode.ALPHABETICAL -> Icon(
                        Icons.Outlined.SortByAlpha,
                        contentDescription = stringResource(MR.strings.action_sort_alpha),
                    )
                    SetMigrateSorting.Mode.TOTAL -> Icon(
                        Icons.Outlined.Numbers,
                        contentDescription = stringResource(MR.strings.action_sort_count),
                    )
                }
            }
            IconButton(onClick = onToggleSortingDirection) {
                when (sortingDirection) {
                    SetMigrateSorting.Direction.ASCENDING -> Icon(
                        Icons.Outlined.ArrowUpward,
                        contentDescription = stringResource(MR.strings.action_asc),
                    )
                    SetMigrateSorting.Direction.DESCENDING -> Icon(
                        Icons.Outlined.ArrowDownward,
                        contentDescription = stringResource(MR.strings.action_desc),
                    )
                }
            }
        }
        Box {
            FastScrollLazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(top = 65.dp),
                // KMK <--
            ) {
                items(
                    items = list
                        // KMK -->
                        .filter {
                            !filterObsoleteSource ||
                                (
                                    it.first.installedExtension?.isObsolete != false &&
                                        (!isHentaiEnabled || it.first.id !in EHENTAI_EXT_SOURCES.keys + EXHENTAI_EXT_SOURCES.keys)
                                    )
                        },
                    // KMK <--
                    key = { (source, _) -> "migrate-${source.id}" },
                ) { (source, count) ->
                    MigrateSourceItem(
                        // KMK -->
                        // modifier = Modifier.animateItem(),
                        modifier = Modifier.animateItemFastScroll(),
                        // KMK <--
                        source = source,
                        count = count,
                        onClickItem = { onClickItem(source) },
                        onLongClickItem = { onLongClickItem(source) },
                    )
                }
            }

            AnimatedFloatingSearchBox(
                listState = lazyListState,
                searchQuery = state.searchQuery,
                onChangeSearchQuery = onChangeSearchQuery,
                placeholderText = stringResource(KMR.strings.action_search_for_source),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    )
                    .align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun MigrateSourceItem(
    source: Source,
    count: Long,
    onClickItem: () -> Unit,
    onLongClickItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        showLanguageInContent = source.lang != "",
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        icon = { SourceIcon(source = source) },
        action = {
            BadgeGroup {
                Badge(text = "$count")
            }
        },
        content = { _, sourceLangString, /* KMK --> */ lang /* KMK <-- */ ->
            Column(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.medium)
                    .weight(1f),
            ) {
                Text(
                    text = source.name.ifBlank { source.id.toString() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (sourceLangString != null) {
                        Text(
                            modifier = Modifier.secondaryItemAlpha(),
                            text = /* KMK --> */ FlagEmoji.getEmojiLangFlag(lang) + " " + /* KMK <-- */
                                sourceLangString,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (source.isStub) {
                        Text(
                            modifier = Modifier.secondaryItemAlpha(),
                            text = stringResource(MR.strings.not_installed),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        // KMK -->
                    } else if (source.installedExtension?.isObsolete == true) {
                        Text(
                            modifier = Modifier.secondaryItemAlpha(),
                            text = stringResource(MR.strings.ext_obsolete),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        // KMK <--
                    }
                }
            }
        },
    )
}
