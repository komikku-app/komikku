package app.komikku.desktop.ui.migration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.CoverImage
import app.komikku.desktop.ui.browse.BrowseScaffold
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.desktop.ui.tabs.SourceIcons
import app.komikku.shared.jssource.MangayomiSourceStore
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreenModel
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrationMangaEvent
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrateSourceScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.selectedBackground

/** Android's migration guide (`MigrateSourceTab`'s help action). */
private const val MIGRATION_GUIDE_URL = "https://komikku-app.github.io/docs/guides/source-migration"

/**
 * P20: Android's Browse → Migrate tab (`MigrateSourceTab` / `MigrateSourceScreen`) on the borrowed
 * [MigrateSourceScreenModel]: the sources with library entries and their entry counts (local entries left out, sources
 * that are not installed first, marked "Not installed", obsolete extensions marked), sorted by name or count in either
 * direction (Android's `migrationSortingMode` / `migrationSortingDirection` preferences), Android's source search ("," for
 * several, the extension's name or a source id), the obsolete-only filter, the migration guide; a click opens the
 * source's library entries ([MigrateMangaScreen]), a long press copies the source id like Android.
 *
 * Desktop deviation: the obsolete filter does not leave out SY's built-in E-Hentai sources (the desktop has none).
 *
 * [obsoleteJsSources]: the ids of installed JavaScript sources gone from every repository (P23); they are filtered and
 * labelled like an obsolete extension. The default resolves the store on the IO dispatcher when collected, not in the
 * composition (P23 review fix).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MigrateSourcePane(
    model: MigrateSourceScreenModel,
    installedExtensions: StateFlow<List<Extension.Installed>> = remember { SourceIcons.installedExtensions() },
    obsoleteJsSources: Flow<Set<Long>> = remember { MangayomiSourceStore.obsoleteSourceIds() },
) {
    val state by model.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var obsoleteOnly by rememberSaveable { mutableStateOf(false) }
    val installed by installedExtensions.collectAsState()
    val obsoleteJs by obsoleteJsSources.collectAsState(emptySet())

    LaunchedEffect(model) {
        model.channel.collect { event ->
            when (event) {
                MigrateSourceScreenModel.Event.FailedFetchingSourcesWithCount -> context.toast(MR.strings.internal_error)
            }
        }
    }

    when {
        state.isLoading -> LoadingScreen()
        // The sources could not be read: say so (not "empty library") and offer to read them again
        state.loadFailed -> EmptyScreen(
            stringRes = MR.strings.internal_error,
            modifier = Modifier.testTag("migrate-sources-error"),
            actions = persistentListOf(EmptyScreenAction(MR.strings.action_retry, Icons.Outlined.Refresh, model::retry)),
        )
        state.searchQuery == null && state.isEmpty -> EmptyScreen(stringRes = MR.strings.information_empty_library)
        else -> Column(modifier = Modifier.fillMaxSize().testTag("migrate-sources")) {
            Row(modifier = Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(MR.strings.migration_selection_prompt),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { uriHandler.openUri(MIGRATION_GUIDE_URL) }) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(MR.strings.migration_help_guide))
                }
                IconButton(onClick = { obsoleteOnly = !obsoleteOnly }, modifier = Modifier.testTag("migrate-sources-obsolete")) {
                    Icon(
                        Icons.Outlined.NewReleases,
                        contentDescription = stringResource(MR.strings.ext_obsolete),
                        tint = if (obsoleteOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                }
                IconButton(onClick = model::toggleSortingMode, modifier = Modifier.testTag("migrate-sources-sort-mode")) {
                    when (state.sortingMode) {
                        SetMigrateSorting.Mode.ALPHABETICAL ->
                            Icon(Icons.Outlined.SortByAlpha, contentDescription = stringResource(MR.strings.action_sort_alpha))
                        SetMigrateSorting.Mode.TOTAL ->
                            Icon(Icons.Outlined.Numbers, contentDescription = stringResource(MR.strings.action_sort_count))
                    }
                }
                IconButton(onClick = model::toggleSortingDirection, modifier = Modifier.testTag("migrate-sources-sort-direction")) {
                    when (state.sortingDirection) {
                        SetMigrateSorting.Direction.ASCENDING ->
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(MR.strings.action_asc))
                        SetMigrateSorting.Direction.DESCENDING ->
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(MR.strings.action_desc))
                    }
                }
            }
            OutlinedTextField(
                value = state.searchQuery.orEmpty(),
                onValueChange = model::search,
                placeholder = { Text(stringResource(KMR.strings.action_search_for_source)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("migrate-sources-search"),
            )
            // Obsolete only: sources that are not installed, those whose extension is obsolete and installed
            // JavaScript sources gone from every repository (P23). Any other source without an APK extension (a
            // Mangayomi JavaScript source still offered) is installed and not obsolete. Re-run when the installed
            // extensions or JavaScript sources change.
            val shown = remember(state.items, obsoleteOnly, installed, obsoleteJs) {
                if (!obsoleteOnly) {
                    state.items
                } else {
                    state.items.filter { (source, _) -> source.isStub || installed.isObsolete(source.id, obsoleteJs) }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("migrate-sources-list")) {
                items(shown, key = { (source, _) -> "migrate-${source.id}" }) { (source, count) ->
                    MigrateSourceItem(
                        source = source,
                        count = count,
                        isObsolete = remember(source.id, installed, obsoleteJs) { installed.isObsolete(source.id, obsoleteJs) },
                        icon = remember(source.id, installed) { SourceIcons.of(source.id, installed) },
                        modifier = Modifier.combinedClickable(
                            onClick = { navigator.push(MigrateMangaScreen(source.id)) },
                            onLongClick = { clipboard.setText(AnnotatedString(source.id.toString())) },
                        ),
                    )
                }
            }
        }
    }
}

/** Whether [sourceId]'s APK extension is obsolete or it is a JavaScript source in [obsoleteJs]. */
private fun List<Extension.Installed>.isObsolete(sourceId: Long, obsoleteJs: Set<Long>): Boolean =
    sourceId in obsoleteJs || extensionOf(sourceId)?.isObsolete == true

/** The installed APK extension that provides [sourceId] (like Android's `Source.installedExtension`). */
private fun List<Extension.Installed>.extensionOf(sourceId: Long): Extension.Installed? =
    find { ext -> ext.sources.any { it.id == sourceId } }

@Composable
private fun MigrateSourceItem(
    source: Source,
    count: Long,
    isObsolete: Boolean,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ListItem(
        modifier = modifier.testTag("migrate-source-${source.id}"),
        leadingContent = {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
            } else {
                Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        },
        headlineContent = {
            Text(text = source.name.ifBlank { source.id.toString() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (source.lang.isNotEmpty()) {
                    Text(
                        text = FlagEmoji.getEmojiLangFlag(source.lang) + " " + LocaleHelper.getSourceDisplayName(source.lang, context),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when {
                    source.isStub -> Text(
                        text = stringResource(MR.strings.not_installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    isObsolete -> Text(
                        text = stringResource(MR.strings.ext_obsolete),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        trailingContent = {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("migrate-source-count-${source.id}"),
            )
        },
        colors = ListItemDefaults.colors(),
    )
}

/**
 * P20: Android's `MigrateMangaScreen` (Browse → Migrate → a source) on the borrowed [MigrateMangaScreenModel]: the
 * source's library entries by title; a click opens the migration configuration for that entry, the cover its details;
 * a long press selects (with Android's range rule), a click then toggles; select all, invert, and "Migrate" opens the
 * configuration for the selection. Desktop deviation: the select / invert / migrate actions are in the top bar (Android:
 * select all in the top bar, "Migrate" and scroll to top / bottom in a bottom bar).
 */
class MigrateMangaScreen(internal val sourceId: Long) : Screen() {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val model = rememberScreenModel { MigrateMangaScreenModel(sourceId) }
        val state by model.state.collectAsState()

        LaunchedEffect(model) {
            model.events.collect { event ->
                when (event) {
                    MigrationMangaEvent.FailedFetchingFavorites -> context.toast(MR.strings.internal_error)
                }
            }
        }
        BackHandler(enabled = state.selectionMode) { model.clearSelection() }

        BrowseScaffold(
            title = if (state.selectionMode) "${state.selection.size}" else state.source?.name.orEmpty(),
            onBack = { if (state.selectionMode) model.clearSelection() else navigator.pop() },
            actions = {
                if (state.titles.isNotEmpty()) {
                    IconButton(onClick = { model.toggleAllSelection(true) }, modifier = Modifier.testTag("migrate-manga-select-all")) {
                        Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(MR.strings.action_select_all))
                    }
                }
                if (state.selectionMode) {
                    IconButton(onClick = model::invertSelection, modifier = Modifier.testTag("migrate-manga-invert")) {
                        Icon(Icons.Outlined.FlipToBack, contentDescription = stringResource(MR.strings.action_select_inverse))
                    }
                    IconButton(
                        onClick = { navigator.push(MigrationConfigScreen(state.selection.toList())) },
                        modifier = Modifier.testTag("migrate-manga-migrate"),
                    ) {
                        Icon(Icons.Outlined.FindReplace, contentDescription = stringResource(MR.strings.migrate))
                    }
                }
            },
        ) {
            when {
                state.isLoading -> LoadingScreen()
                state.isEmpty -> EmptyScreen(stringRes = MR.strings.empty_screen)
                else -> LazyColumn(modifier = Modifier.fillMaxSize().testTag("migrate-manga-list")) {
                    items(state.titles, key = { it.id }) { manga ->
                        val isSelected = manga.id in state.selection
                        MigrateMangaItem(
                            manga = manga,
                            isSelected = isSelected,
                            onClick = {
                                if (state.selectionMode) {
                                    model.toggleSelection(manga, !isSelected)
                                } else {
                                    navigator.push(MigrationConfigScreen(manga.id))
                                }
                            },
                            onLongClick = { model.toggleSelection(manga, !isSelected, fromLongPress = true) },
                            onClickCover = { navigator.push(MangaDetailsScreen(manga.id)) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MigrateMangaItem(
    manga: Manga,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectedBackground(isSelected)
            .semantics { selected = isSelected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("migrate-manga-${manga.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverImage(
            model = remember(manga) { manga.asMangaCover() },
            contentDescription = null,
            modifier = Modifier
                .width(40.dp)
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClick = onClickCover)
                .testTag("migrate-manga-cover-${manga.id}"),
        )
        Text(
            text = manga.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
