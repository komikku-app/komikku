package app.komikku.desktop.ui.migration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.CoverImage
import app.komikku.desktop.ui.browse.BrowseScaffold
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.desktop.ui.manga.MigrateSearchScreen
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.util.system.toast
import mihon.feature.migration.config.MigrationConfigScreenSheet
import mihon.feature.migration.list.MigrationListScreenModel
import mihon.feature.migration.list.components.MigrationExitDialog
import mihon.feature.migration.list.components.MigrationMangaDialog
import mihon.feature.migration.list.components.MigrationProgressDialog
import mihon.feature.migration.list.models.MigratingManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource

/**
 * P19: Android's `MigrationListScreen` (Mihon / Komikku) on desktop, on the borrowed [MigrationListScreenModel]: each
 * entry with its automatic match from the migration sources (in their order, or the one with the most chapters with
 * "prioritise by chapters"; deep search; the extra query; SY's throttle), searching / no match / the match, per entry
 * cancel the search, search manually (the migration search, whose pick comes back here as the entry's match), skip,
 * migrate / copy now; the top bar's options (the borrowed sheet without the start-only settings), copy all and migrate
 * all (Android's confirmation with the skipped count and the progress dialog with cancel); leaving asks first (Android's
 * exit dialog). With one entry and no smart search for one entry (Android's default) no automatic search runs and the
 * migration search opens at once. When the list is done the screen closes; for one entry opened from its details, the
 * new entry's details take the old one's place, like Android.
 */
class MigrationListScreen(
    internal val mangaIds: Collection<Long>,
    private val extraSearchQuery: String?,
    private val isSmartSearchSingleEntry: Boolean = false,
) : Screen() {

    private var matchOverride by mutableStateOf<Pair<Long, Long>?>(null)

    /** Android's: the migration search's pick for [current]. */
    fun addMatchOverride(current: Long, target: Long) {
        matchOverride = current to target
    }

    @Composable
    override fun Content() {
        // Review fix (P19): the model resolves the migration sources once, so it is created only once they are loaded
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val sourcesLoaded by sourceManager.isInitialized.collectAsState()
        if (!sourcesLoaded) {
            LoadingScreen()
            return
        }
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val singleEntryNoSmartSearch = mangaIds.size == 1 && !isSmartSearchSingleEntry
        val model = rememberScreenModel { MigrationListScreenModel(mangaIds, extraSearchQuery, singleEntryNoSmartSearch) }
        val state by model.state.collectAsState()

        // Saveable like Android's: this screen is not composed while the migration search is on top of it
        var hasPushedManual by rememberSaveable(mangaIds) { mutableStateOf(false) }
        LaunchedEffect(mangaIds) {
            if (singleEntryNoSmartSearch && !hasPushedManual) {
                hasPushedManual = true
                navigator.push(MigrateSearchScreen(mangaIds.single()))
            }
        }

        LaunchedEffect(matchOverride) {
            val (current, target) = matchOverride ?: return@LaunchedEffect
            model.useMangaForMigration(
                current = current,
                target = target,
                onMissingChapters = { context.toast(MR.strings.migrationListScreen_matchWithoutChapterToast) },
            )
            matchOverride = null
        }

        LaunchedEffect(model) {
            model.navigateBackEvent.collect {
                // Review fix (P19): after "migrate / copy now" the entry is no longer in the list; the last target is
                val newId = model.lastMigratedTargetId
                    ?: (model.state.value.items.firstOrNull()?.searchResult?.value as? MigratingManga.SearchResult.Success)?.manga?.id
                navigator.leaveMigration(list = this@MigrationListScreen, migratedId = mangaIds.singleOrNull(), newMangaId = newId)
            }
        }
        // Review fix (P19): a failed migration is reported; the entry stays in the list
        LaunchedEffect(model) {
            model.migrationFailedEvent.collect { manga ->
                context.toast(context.contextStringResource(KMR.strings.desktop_migration_failed, manga.title))
            }
        }

        BackHandler(true) { model.showExitDialog() }

        BrowseScaffold(
            title = if (state.items.isNotEmpty()) {
                stringResource(MR.strings.migrationListScreenTitleWithProgress, state.finishedCount, state.items.size)
            } else {
                stringResource(MR.strings.migrationListScreenTitle)
            },
            onBack = model::showExitDialog,
            actions = {
                IconButton(onClick = model::openOptionsDialog, modifier = Modifier.testTag("migration-list-options")) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.action_settings))
                }
                IconButton(
                    onClick = { model.showMigrateDialog(true) },
                    enabled = state.migrationComplete,
                    modifier = Modifier.testTag("migration-list-copy"),
                ) {
                    Icon(
                        if (state.items.size == 1) Icons.Outlined.ContentCopy else Icons.Outlined.CopyAll,
                        contentDescription = stringResource(MR.strings.migrationListScreen_copyActionLabel),
                    )
                }
                IconButton(
                    onClick = { model.showMigrateDialog(false) },
                    enabled = state.migrationComplete,
                    modifier = Modifier.testTag("migration-list-migrate"),
                ) {
                    Icon(
                        if (state.items.size == 1) Icons.Outlined.Done else Icons.Outlined.DoneAll,
                        contentDescription = stringResource(MR.strings.migrationListScreen_migrateActionLabel),
                    )
                }
            },
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("migration-list")) {
                items(state.items, key = { it.manga.id }) { item ->
                    val result by item.searchResult.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("migration-item-${item.manga.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MigrationEntry(
                            manga = item.manga,
                            source = item.source,
                            chapterCount = item.chapterCount,
                            latestChapter = item.latestChapter,
                            onClick = { navigator.push(MangaDetailsScreen(item.manga.id)) },
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                        Box(modifier = Modifier.weight(1f).testTag("migration-result-${item.manga.id}")) {
                            when (val current = result) {
                                MigratingManga.SearchResult.Searching -> Box(
                                    modifier = Modifier.width(ENTRY_WIDTH).aspectRatio(COVER_RATIO),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator() }
                                MigratingManga.SearchResult.NotFound -> Text(
                                    text = stringResource(MR.strings.migrationListScreen_noMatchFoundText),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(4.dp),
                                )
                                is MigratingManga.SearchResult.Success -> MigrationEntry(
                                    manga = current.manga,
                                    source = current.source,
                                    chapterCount = current.chapterCount,
                                    latestChapter = current.latestChapter,
                                    onClick = { navigator.push(MangaDetailsScreen(current.manga.id)) },
                                )
                            }
                        }
                        MigrationItemAction(
                            mangaId = item.manga.id,
                            result = result,
                            onSearchManually = { navigator.push(MigrateSearchScreen(item.manga.id)) },
                            onSkip = { model.removeManga(item.manga.id) },
                            onMigrate = { model.migrateNow(mangaId = item.manga.id, replace = true) },
                            onCopy = { model.migrateNow(mangaId = item.manga.id, replace = false) },
                            onCancel = { model.cancelManga(item.manga.id) },
                        )
                    }
                }
            }
        }

        when (val dialog = state.dialog) {
            is MigrationListScreenModel.Dialog.Migrate -> MigrationMangaDialog(
                onDismissRequest = model::dismissDialog,
                copy = dialog.copy,
                totalCount = dialog.totalCount,
                skippedCount = dialog.skippedCount,
                onMigrate = { if (dialog.copy) model.copyMangas() else model.migrateMangas() },
            )
            is MigrationListScreenModel.Dialog.Progress -> MigrationProgressDialog(
                progress = dialog.progress,
                exitMigration = model::cancelMigrate,
            )
            MigrationListScreenModel.Dialog.Exit -> MigrationExitDialog(
                onDismissRequest = model::dismissDialog,
                exitMigration = navigator::pop,
            )
            MigrationListScreenModel.Dialog.Options -> MigrationConfigScreenSheet(
                preferences = model.preferences,
                onDismissRequest = model::dismissDialog,
                onStartMigration = { _ ->
                    model.dismissDialog()
                    model.updateOptions()
                },
                fullSettings = false,
            )
            null -> Unit
        }
    }

    private companion object {
        val ENTRY_WIDTH = 120.dp
        const val COVER_RATIO = 2f / 3f
    }
}

/**
 * Android's end of the list: [list] and the screens above it close, and its own configuration screen if it is right below;
 * for one entry ([migratedId]) whose details are right below, the details of the new entry ([newMangaId]) take their
 * place. Every other screen stays (review fix, P19: other details, another migration list).
 */
private fun Navigator.leaveMigration(list: MigrationListScreen, migratedId: Long?, newMangaId: Long?) {
    val index = items.indexOfLast { it === list }
    if (index <= 0) {
        pop()
        return
    }
    val below = items.subList(0, index).toMutableList()
    val config = below.last() as? MigrationConfigScreen
    if (below.size > 1 && config != null && config.mangaIds.toSet() == list.mangaIds.toSet()) below.removeAt(below.lastIndex)
    val details = below.last() as? MangaDetailsScreen
    val swapDetails = migratedId != null && newMangaId != null && details?.mangaId == migratedId
    if (swapDetails) below[below.lastIndex] = MangaDetailsScreen(newMangaId)
    if (!swapDetails && below.size == index && items.size == index + 1) {
        pop()
        return
    }
    replaceAll(below)
    // Like Android: end in a pop so the screens taken out are disposed properly
    push(list)
    pop()
}

@Composable
private fun MigrationEntry(
    manga: Manga,
    source: String,
    chapterCount: Int,
    latestChapter: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoverImage(
            model = remember(manga) { manga.asMangaCover() },
            contentDescription = null,
            modifier = Modifier.width(64.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.extraSmall),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = manga.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            Text(text = source, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(
                    MR.strings.migrationListScreen_latestChapterLabel,
                    latestChapter?.let(::formatChapterNumber) ?: stringResource(MR.strings.migrationListScreen_unknownLatestChapter),
                ),
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = "$chapterCount", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MigrationItemAction(
    mangaId: Long,
    result: MigratingManga.SearchResult,
    onSearchManually: () -> Unit,
    onSkip: () -> Unit,
    onMigrate: () -> Unit,
    onCopy: () -> Unit,
    onCancel: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        when (result) {
            MigratingManga.SearchResult.Searching -> IconButton(onClick = onCancel, modifier = Modifier.testTag("migration-cancel-$mangaId")) {
                Icon(Icons.Outlined.Close, contentDescription = null)
            }
            MigratingManga.SearchResult.NotFound, is MigratingManga.SearchResult.Success -> {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag("migration-menu-$mangaId")) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.migrationListScreen_searchManuallyActionLabel)) },
                        onClick = {
                            menuExpanded = false
                            onSearchManually()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.migrationListScreen_skipActionLabel)) },
                        onClick = {
                            menuExpanded = false
                            onSkip()
                        },
                    )
                    if (result is MigratingManga.SearchResult.Success) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.migrationListScreen_migrateNowActionLabel)) },
                            onClick = {
                                menuExpanded = false
                                onMigrate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.migrationListScreen_copyNowActionLabel)) },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            },
                        )
                    }
                }
            }
        }
    }
}
