package eu.kanade.tachiyomi.ui.anime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.materialkolor.ktx.blend
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.anime.model.hasCustomCover
import eu.kanade.domain.anime.model.toSManga
import eu.kanade.presentation.anime.AnimeScreen
import eu.kanade.presentation.anime.DuplicateAnimeDialog
import eu.kanade.presentation.anime.EditCoverAction
import eu.kanade.presentation.anime.EpisodeSettingsDialog
import eu.kanade.presentation.anime.components.AnimeCoverDialog
import eu.kanade.presentation.anime.components.DeleteEpisodesDialog
import eu.kanade.presentation.anime.components.ScanlatorFilterDialog
import eu.kanade.presentation.anime.components.SetIntervalDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.anime.merged.EditMergedSettingsDialog
import eu.kanade.tachiyomi.ui.anime.track.TrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.browse.AddDuplicateAnimeDialog
import eu.kanade.tachiyomi.ui.browse.AllowDuplicateDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.ChangeAnimeCategoryDialog
import eu.kanade.tachiyomi.ui.browse.ChangeAnimesCategoryDialog
import eu.kanade.tachiyomi.ui.browse.RemoveAnimeDialog
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import exh.md.similar.MangaDexSimilarScreen
import exh.pagepreview.PagePreviewScreen
import exh.pref.DelegateSourcePreferences
import exh.recs.RecommendsScreen
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import exh.source.isMdBasedSource
import exh.ui.metadata.MetadataViewScreen
import exh.ui.smartsearch.SmartSearchScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.anime.model.Manga
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.interactor.GetRemoteAnime
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeScreen(
    private val mangaId: Long,
    /** If it is opened from Source then it will auto expand the manga description */
    val fromSource: Boolean = false,
    private val smartSearchConfig: SourcesScreen.SmartSearchConfig? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val screenModel = rememberScreenModel {
            AnimeScreenModel(context, lifecycleOwner.lifecycle, mangaId, fromSource, smartSearchConfig != null)
        }

        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is AnimeScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeScreenModel.State.Success

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val showingRelatedMangasScreen = rememberSaveable { mutableStateOf(false) }

        BackHandler(enabled = bulkFavoriteState.selectionMode || showingRelatedMangasScreen.value) {
            when {
                bulkFavoriteState.selectionMode -> bulkFavoriteScreenModel.toggleSelectionMode()
                showingRelatedMangasScreen.value -> showingRelatedMangasScreen.value = false
            }
        }

        val content = @Composable {
            Crossfade(
                targetState = showingRelatedMangasScreen.value,
                label = "manga_related_crossfade",
            ) { showRelatedMangasScreen ->
                when (showRelatedMangasScreen) {
                    true -> RelatedAnimesScreen(
                        screenModel = screenModel,
                        successState = successState,
                        bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                        navigateUp = { showingRelatedMangasScreen.value = false },
                        navigator = navigator,
                        scope = scope,
                    )
                    false -> MangaDetailContent(
                        context = context,
                        screenModel = screenModel,
                        successState = successState,
                        bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                        showRelatedMangasScreen = { showingRelatedMangasScreen.value = true },
                        navigator = navigator,
                        scope = scope,
                    )
                }
            }
        }

        val seedColor = successState.seedColor
        TachiyomiTheme(
            seedColor = seedColor.takeIf { screenModel.themeCoverBased },
        ) {
            content()
        }

        when (bulkFavoriteState.dialog) {
            is BulkFavoriteScreenModel.Dialog.AddDuplicateManga ->
                AddDuplicateAnimeDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.RemoveManga ->
                RemoveAnimeDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.ChangeMangaCategory ->
                ChangeAnimeCategoryDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.ChangeMangasCategory ->
                ChangeAnimesCategoryDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.AllowDuplicate ->
                AllowDuplicateDialog(bulkFavoriteScreenModel)

            else -> {}
        }
    }

    @Composable
    fun MangaDetailContent(
        context: Context,
        screenModel: AnimeScreenModel,
        successState: AnimeScreenModel.State.Success,
        bulkFavoriteScreenModel: BulkFavoriteScreenModel,
        showRelatedMangasScreen: () -> Unit,
        navigator: Navigator,
        scope: CoroutineScope,
    ) {
        // KMK <--
        val haptic = LocalHapticFeedback.current
        val isHttpSource = remember { successState.source is AnimeHttpSource }

        LaunchedEffect(successState.manga, screenModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getMangaUrl(screenModel.manga, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
                }
            }
        }

        // SY -->
        LaunchedEffect(Unit) {
            screenModel.redirectFlow
                .take(1)
                .onEach {
                    navigator.replace(
                        AnimeScreen(it.mangaId),
                    )
                }
                .launchIn(this)
        }
        // SY <--

        // KMK -->
        val coverRatio = remember { mutableFloatStateOf(1f) }
        val hazeState = remember { HazeState() }
        val fullCoverBackground = MaterialTheme.colorScheme.surfaceTint.blend(MaterialTheme.colorScheme.surface)
        // KMK <--

        AnimeScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
            chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
            onBackClicked = navigator::pop,
            onChapterClicked = { openChapter(context, it) },
            onDownloadChapter = screenModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            // SY -->
            onWebViewClicked = {
                if (successState.mergedData == null) {
                    openMangaInWebView(
                        navigator,
                        screenModel.manga,
                        screenModel.source,
                    )
                } else {
                    openMergedMangaWebview(
                        context,
                        navigator,
                        successState.mergedData,
                    )
                }
            }.takeIf { isHttpSource },
            // SY <--
            onWebViewLongClicked = {
                copyMangaUrl(
                    context,
                    screenModel.manga,
                    screenModel.source,
                )
            }.takeIf { isHttpSource },
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    screenModel.showTrackDialog()
                }
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onRefresh = screenModel::fetchAllFromSource,
            onContinueReading = { continueReading(context, screenModel.getNextUnreadChapter()) },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            // KMK -->
            librarySearch = { query ->
                scope.launch { performSearch(navigator, query, global = false, library = true) }
            },
            // KMK <--
            onCoverClicked = screenModel::showCoverDialog,
            onShareClicked = { shareManga(context, screenModel.manga, screenModel.source) }.takeIf { isHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
            onEditCategoryClicked = screenModel::showChangeCategoryDialog.takeIf { successState.manga.favorite },
            onEditFetchIntervalClicked = screenModel::showSetFetchIntervalDialog.takeIf {
                successState.manga.favorite
            },
            previewsRowCount = successState.previewsRowCount,
            // SY -->
            onMigrateClicked = { migrateManga(navigator, screenModel.manga!!) }.takeIf { successState.manga.favorite },
            onMetadataViewerClicked = {
                openMetadataViewer(
                    navigator,
                    successState.manga,
                    // KMK -->
                    successState.seedColor,
                    // KMK <--
                )
            },
            onEditInfoClicked = screenModel::showEditMangaInfoDialog,
            onRecommendClicked = {
                openRecommends(context, navigator, screenModel.source?.getMainSource(), successState.manga)
            },
            onMergedSettingsClicked = screenModel::showEditMergedSettingsDialog,
            onMergeClicked = { openSmartSearch(navigator, successState.manga) },
            onMergeWithAnotherClicked = {
                mergeWithAnother(navigator, context, successState.manga, screenModel::smartSearchMerge)
            },
            onOpenPagePreview = { page ->
                openPagePreview(context, successState.chapters.minByOrNull { it.episode.sourceOrder }?.episode, page)
            },
            onMorePreviewsClicked = { openMorePagePreviews(navigator, successState.manga) },
            // SY <--
            onMultiBookmarkClicked = screenModel::bookmarkChapters,
            onMultiMarkAsReadClicked = screenModel::markChaptersRead,
            onMarkPreviousAsReadClicked = screenModel::markPreviousChapterRead,
            onMultiDeleteClicked = screenModel::showDeleteChapterDialog,
            onChapterSwipe = screenModel::chapterSwipe,
            onChapterSelected = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            // KMK -->
            getMangaState = { screenModel.getManga(initialManga = it) },
            onRelatedMangasScreenClick = {
                if (successState.isRelatedMangasFetched == null) {
                    scope.launchIO { screenModel.fetchRelatedMangasFromSource(onDemand = true) }
                }
                showRelatedMangasScreen()
            },
            onRelatedMangaClick = {
                scope.launchIO {
                    val manga = screenModel.networkToLocalAnime.getLocal(it)
                    navigator.push(AnimeScreen(manga.id, true))
                }
            },
            onRelatedMangaLongClick = {
                scope.launchIO {
                    val manga = screenModel.networkToLocalAnime.getLocal(it)
                    bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                }
            },
            onSourceClick = {
                if (successState.source !is StubSource) {
                    val screen = when {
                        // Clicked on source of an entry being merged with previous entry or
                        // source of an recommending entry (to search again)
                        smartSearchConfig != null -> SmartSearchScreen(successState.source.id, smartSearchConfig)
                        screenModel.useNewSourceNavigation -> SourceFeedScreen(successState.source.id)
                        else -> BrowseSourceScreen(successState.source.id, GetRemoteAnime.QUERY_POPULAR)
                    }
                    when (screen) {
                        // When doing a migrate/recommend => replace previous screen to perform search again.
                        is SmartSearchScreen -> {
                            navigator.popUntil { it is SmartSearchScreen }
                            if (navigator.size > 1) navigator.replace(screen) else navigator.push(screen)
                        }
                        is SourceFeedScreen -> {
                            navigator.popUntil { it is SourceFeedScreen }
                            if (navigator.size > 1) navigator.replace(screen) else navigator.push(screen)
                        }
                        else -> {
                            navigator.popUntil { it is BrowseSourceScreen }
                            if (navigator.size > 1) navigator.replace(screen) else navigator.push(screen)
                        }
                    }
                } else {
                    navigator.push(ExtensionsScreen(searchSource = successState.source.name))
                }
            },
            onCoverLoaded = {
                if (screenModel.themeCoverBased || successState.manga.favorite) screenModel.setPaletteColor(it)
            },
            coverRatio = coverRatio,
            onPaletteScreenClick = { navigator.push(PaletteScreen(successState.seedColor?.toArgb())) },
            hazeState = hazeState,
            // KMK <--
        )

        var showScanlatorsDialog by remember { mutableStateOf(false) }

        val onDismissRequest = { screenModel.dismissDialog() }
        when (val dialog = successState.dialog) {
            null -> {}
            is AnimeScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }

            is AnimeScreenModel.Dialog.DeleteChapters -> {
                DeleteEpisodesDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteChapters(dialog.episodes)
                    },
                )
            }

            is AnimeScreenModel.Dialog.DuplicateManga -> {
                DuplicateAnimeDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenManga = { navigator.push(AnimeScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        // SY -->
                        migrateManga(navigator, dialog.duplicate, screenModel.manga!!.id)
                        // SY <--
                    },
                    // KMK -->
                    duplicate = dialog.duplicate,
                    // KMK <--
                )
            }

            AnimeScreenModel.Dialog.SettingsSheet -> EpisodeSettingsDialog(
                onDismissRequest = onDismissRequest,
                manga = successState.manga,
                onDownloadFilterChanged = screenModel::setDownloadedFilter,
                onUnreadFilterChanged = screenModel::setUnreadFilter,
                onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                onSortModeChanged = screenModel::setSorting,
                onDisplayModeChanged = screenModel::setDisplayMode,
                onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
                onResetToDefault = screenModel::resetToDefaultSettings,
                scanlatorFilterActive = successState.scanlatorFilterActive,
                onScanlatorFilterClicked = { showScanlatorsDialog = true },
            )

            AnimeScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = TrackInfoDialogHomeScreen(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is TrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }

            AnimeScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { AnimeCoverScreenModel(successState.manga.id) }
                val manga by sm.state.collectAsState()
                if (manga != null) {
                    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    // KMK -->
                    val externalStoragePermissionNotGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_DENIED
                    val saveCoverPermissionRequester = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = {
                            sm.saveCover(context)
                        },
                    )
                    // KMK <--
                    AnimeCoverDialog(
                        manga = manga!!,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(manga) { manga!!.hasCustomCover() },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = {
                            // KMK -->
                            if (externalStoragePermissionNotGranted) {
                                saveCoverPermissionRequester.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                // KMK <--
                                sm.saveCover(context)
                            }
                        },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                        // KMK -->
                        modifier = Modifier
                            .hazeChild(
                                state = hazeState,
                                style = HazeStyle(
                                    backgroundColor = Color.Transparent,
                                    tint = HazeDefaults.tint(fullCoverBackground),
                                    blurRadius = 10.dp,
                                ),
                            ),
                        // KMK <--
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }

            is AnimeScreenModel.Dialog.SetFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.manga, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
            // SY -->
            is AnimeScreenModel.Dialog.EditMangaInfo -> {
                EditAnimeDialog(
                    manga = dialog.manga,
                    // KMK -->
                    coverRatio = coverRatio,
                    // KMK <--
                    onDismissRequest = screenModel::dismissDialog,
                    onPositiveClick = screenModel::updateMangaInfo,
                )
            }

            is AnimeScreenModel.Dialog.EditMergedSettings -> {
                EditMergedSettingsDialog(
                    mergedData = dialog.mergedData,
                    onDismissRequest = screenModel::dismissDialog,
                    onDeleteClick = screenModel::deleteMerge,
                    onPositiveClick = screenModel::updateMergeSettings,
                )
            }
            // SY <--
        }

        if (showScanlatorsDialog) {
            ScanlatorFilterDialog(
                availableScanlators = successState.availableScanlators,
                excludedScanlators = successState.excludedScanlators,
                onDismissRequest = { showScanlatorsDialog = false },
                onConfirm = screenModel::setExcludedScanlators,
            )
        }
    }

    private fun continueReading(context: Context, unreadEpisode: Episode?) {
        if (unreadEpisode != null) openChapter(context, unreadEpisode)
    }

    private fun openChapter(context: Context, episode: Episode) {
        context.startActivity(ReaderActivity.newIntent(context, episode.mangaId, episode.id))
    }

    @Suppress("LocalVariableName")
    private fun getMangaUrl(manga_: Manga?, source_: AnimeSource?): String? {
        val manga = manga_ ?: return null
        val source = source_ as? AnimeHttpSource ?: return null

        return try {
            source.getAnimeUrl(manga.toSManga())
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("LocalVariableName")
    private fun openMangaInWebView(navigator: Navigator, manga_: Manga?, source_: AnimeSource?) {
        getMangaUrl(manga_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = manga_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    @Suppress("LocalVariableName")
    private fun shareManga(context: Context, manga_: Manga?, source_: AnimeSource?) {
        try {
            getMangaUrl(manga_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        context.stringResource(MR.strings.action_share),
                    ),
                )
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(
        navigator: Navigator,
        query: String,
        global: Boolean,
        // KMK -->
        library: Boolean = false,
        // KMK <--
    ) {
        if (global) {
            navigator.push(GlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        // KMK -->
        navigator.popUntil { screen ->
            screen is HomeScreen ||
                !library &&
                (screen is BrowseSourceScreen || screen is SourceFeedScreen)
        }
        // KMK <--

        when (val previousController = navigator.lastItem) {
            is HomeScreen -> {
                // KMK -->
                // navigator.pop()
                // KMK <--
                previousController.search(query)
            }
            is BrowseSourceScreen -> {
                // KMK -->
                // navigator.pop()
                // KMK <--
                previousController.search(query)
            }
            // SY -->
            is SourceFeedScreen -> {
                // KMK -->
                // navigator.pop()
                // navigator.replace(BrowseSourceScreen(previousController.sourceId, query))
                navigator.push(BrowseSourceScreen(previousController.sourceId, query))
                // KMK <--
            }
            // SY <--
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(navigator: Navigator, genreName: String, source: AnimeSource) {
        if (navigator.size < 2) {
            return
        }

        var previousController: cafe.adriel.voyager.core.screen.Screen
        var idx = navigator.size - 2
        while (idx >= 0) {
            previousController = navigator.items[idx--]
            if (previousController is BrowseSourceScreen && source is AnimeHttpSource) {
                // KMK -->
                // navigator.pop()
                navigator.popUntil { navigator.size == idx + 2 }
                // KMK <--
                previousController.searchGenre(genreName)
                return
            }
            // KMK -->
            if (previousController is SourceFeedScreen && source is AnimeHttpSource) {
                navigator.popUntil { navigator.size == idx + 2 }
                navigator.push(BrowseSourceScreen(previousController.sourceId, ""))
                previousController = navigator.lastItem as BrowseSourceScreen
                previousController.searchGenre(genreName)
                return
            }
            // KMK <--
        }
        performSearch(navigator, genreName, global = false)
    }

    /**
     * Copy Manga URL to Clipboard
     */
    @Suppress("LocalVariableName")
    private fun copyMangaUrl(context: Context, manga_: Manga?, source_: AnimeSource?) {
        val manga = manga_ ?: return
        val source = source_ as? AnimeHttpSource ?: return
        val url = source.getAnimeUrl(manga.toSManga())
        context.copyToClipboard(url, url)
    }

    // SY -->
    /**
     * Initiates source migration for the specific manga.
     */
    private fun migrateManga(navigator: Navigator, manga: Manga, toMangaId: Long? = null) {
        // SY -->
        PreMigrationScreen.navigateToMigration(
            Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
            navigator,
            manga.id,
            toMangaId,
        )
        // SY <--
    }

    private fun openMetadataViewer(
        navigator: Navigator,
        manga: Manga,
        // KMK -->
        seedColor: Color?,
        // KMK <--
    ) {
        navigator.push(MetadataViewScreen(manga.id, manga.source, seedColor?.toArgb()))
    }

    private fun openMergedMangaWebview(context: Context, navigator: Navigator, mergedMangaData: MergedMangaData) {
        val sourceManager: SourceManager = Injekt.get()
        val mergedManga = mergedMangaData.manga.values.filterNot { it.source == MERGED_SOURCE_ID }
        val sources = mergedManga.map { sourceManager.getOrStub(it.source) }
        MaterialAlertDialogBuilder(context)
            .setTitle(MR.strings.action_open_in_web_view.getString(context))
            .setSingleChoiceItems(
                Array(mergedManga.size) { index -> sources[index].toString() },
                -1,
            ) { dialog, index ->
                dialog.dismiss()
                openMangaInWebView(navigator, mergedManga[index], sources[index] as? AnimeHttpSource)
            }
            .setNegativeButton(MR.strings.action_cancel.getString(context), null)
            .show()
    }

    private fun openMorePagePreviews(navigator: Navigator, manga: Manga) {
        navigator.push(PagePreviewScreen(manga.id))
    }

    private fun openPagePreview(context: Context, episode: Episode?, page: Int) {
        episode ?: return
        context.startActivity(ReaderActivity.newIntent(context, episode.mangaId, episode.id, page))
    }
    // SY <--

    // EXH -->
    /**
     * Called when click Merge on an entry to search for entries to merge.
     */
    private fun openSmartSearch(navigator: Navigator, manga: Manga) {
        val smartSearchConfig = SourcesScreen.SmartSearchConfig(manga.title, manga.id)

        navigator.push(SourcesScreen(smartSearchConfig))
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun mergeWithAnother(
        navigator: Navigator,
        context: Context,
        manga: Manga,
        smartSearchMerge: suspend (Manga, Long) -> Manga,
    ) {
        launchUI {
            try {
                val mergedManga = withNonCancellableContext {
                    smartSearchMerge(manga, smartSearchConfig?.origMangaId!!)
                }

                navigator.popUntil { it is SourcesScreen }
                navigator.pop()
                // KMK -->
                if (navigator.lastItem !is AnimeScreen) {
                    navigator push AnimeScreen(mergedManga.id)
                } else {
                    // KMK <--
                    navigator replace AnimeScreen(mergedManga.id)
                }
                context.toast(SYMR.strings.entry_merged)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                context.toast(context.stringResource(SYMR.strings.failed_merge, e.message.orEmpty()))
            }
        }
    }
    // EXH <--

    // AZ -->
    private fun openRecommends(context: Context, navigator: Navigator, source: AnimeSource?, manga: Manga) {
        source ?: return
        if (source.isMdBasedSource() && Injekt.get<DelegateSourcePreferences>().delegateSources().get()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(SYMR.strings.az_recommends.getString(context))
                .setSingleChoiceItems(
                    arrayOf(
                        context.stringResource(SYMR.strings.mangadex_similar),
                        context.stringResource(SYMR.strings.community_recommendations),
                    ),
                    -1,
                ) { dialog, index ->
                    dialog.dismiss()
                    when (index) {
                        0 -> navigator.push(MangaDexSimilarScreen(manga.id, source.id))
                        1 -> navigator.push(RecommendsScreen(manga.id, source.id))
                    }
                }
                .show()
        } else if (source is AnimeCatalogueSource) {
            navigator.push(RecommendsScreen(manga.id, source.id))
        }
    }
    // AZ <--
}
