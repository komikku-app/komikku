package eu.kanade.tachiyomi.ui.manga

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
import dev.chrisbanes.haze.hazeEffect
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.manga.ChapterSettingsDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.presentation.manga.MangaScreen
import eu.kanade.presentation.manga.components.DeleteChaptersDialog
import eu.kanade.presentation.manga.components.MangaCoverDialog
import eu.kanade.presentation.manga.components.ScanlatorFilterDialog
import eu.kanade.presentation.manga.components.SetIntervalDialog
import eu.kanade.presentation.more.settings.screen.SettingsEhScreen
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreen
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateDialog
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.merged.EditMergedSettingsDialog
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import exh.pagepreview.PagePreviewScreen
import exh.recs.RecommendsScreen
import exh.source.MERGED_SOURCE_ID
import exh.source.anyIs
import exh.source.getMainSource
import exh.source.isEhBasedSource
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
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaScreen(
    private val mangaId: Long,
    /**
     * If it is opened from Source then it will auto expand the manga description.
     * - `true`: Expand description if it's not favorited
     * - `false`: Don't expand description
     */
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
            MangaScreenModel(
                context = context,
                lifecycle = lifecycleOwner.lifecycle,
                mangaId = mangaId,
                isFromSource = fromSource,
                smartSearched = smartSearchConfig != null,
            )
        }

        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is MangaScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaScreenModel.State.Success

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val showingRelatedMangasScreen = rememberSaveable { mutableStateOf(false) }

        BackHandler(enabled = bulkFavoriteState.selectionMode || showingRelatedMangasScreen.value) {
            when {
                bulkFavoriteState.selectionMode -> bulkFavoriteScreenModel.backHandler()
                showingRelatedMangasScreen.value -> showingRelatedMangasScreen.value = false
            }
        }

        val content = @Composable {
            Crossfade(
                targetState = showingRelatedMangasScreen.value,
                label = "manga_related_crossfade",
            ) { showRelatedMangasScreen ->
                when (showRelatedMangasScreen) {
                    true -> RelatedMangasScreen(
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

        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
    }

    @Composable
    fun MangaDetailContent(
        context: Context,
        screenModel: MangaScreenModel,
        successState: MangaScreenModel.State.Success,
        bulkFavoriteScreenModel: BulkFavoriteScreenModel,
        showRelatedMangasScreen: () -> Unit,
        navigator: Navigator,
        scope: CoroutineScope,
    ) {
        // KMK <--
        val haptic = LocalHapticFeedback.current
        val isHttpSource = remember { successState.source is HttpSource }

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
                        MangaScreen(it.mangaId),
                    )
                }
                .launchIn(this)
        }
        // SY <--

        // KMK -->
        val coverRatio = remember { mutableFloatStateOf(1f) }
        val hazeState = remember { HazeState() }
        val fullCoverBackground = MaterialTheme.colorScheme.surfaceTint.blend(MaterialTheme.colorScheme.surface)

        val isHentaiEnabled: Boolean = Injekt.get<UnsortedPreferences>().isHentaiEnabled().get()
        val isConfigurableSource = successState.source.anyIs<ConfigurableSource>() ||
            successState.source.isEhBasedSource() &&
            isHentaiEnabled
        // KMK <--

        MangaScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
            chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
            navigateUp = navigator::pop,
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
            onMigrateClicked = {
                // SY -->
                PreMigrationScreen.navigateToMigration(
                    Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                    navigator,
                    listOfNotNull(successState.manga.id),
                )
                // SY <--
            }.takeIf { successState.manga.favorite },
            // SY -->
            previewsRowCount = successState.previewsRowCount,
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
                openRecommends(navigator, screenModel.source?.getMainSource(), successState.manga)
            },
            onMergedSettingsClicked = screenModel::showEditMergedSettingsDialog,
            onMergeClicked = { openSmartSearch(navigator, successState.manga) },
            onMergeWithAnotherClicked = {
                mergeWithAnother(navigator, context, successState.manga, screenModel::smartSearchMerge)
            },
            onOpenPagePreview = { page ->
                openPagePreview(context, successState.chapters.minByOrNull { it.chapter.sourceOrder }?.chapter, page)
            },
            onMorePreviewsClicked = { openMorePagePreviews(navigator, successState.manga) },
            // SY <--
            onEditNotesClicked = { navigator.push(MangaNotesScreen(manga = successState.manga)) },
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
            onClickSourceSettingsClicked = {
                when {
                    successState.source.isEhBasedSource() && isHentaiEnabled ->
                        navigator.push(SettingsEhScreen)
                    successState.source.anyIs<ConfigurableSource>() ->
                        navigator.push(SourcePreferencesScreen(successState.source.id))
                    else -> {}
                }
            }.takeIf { isConfigurableSource },
            onRelatedMangasScreenClick = {
                if (successState.isRelatedMangasFetched == null) {
                    scope.launchIO { screenModel.fetchRelatedMangasFromSource(onDemand = true) }
                }
                showRelatedMangasScreen()
            },
            onRelatedMangaClick = { navigator.push(MangaScreen(it.id, true)) },
            onRelatedMangaLongClick = { bulkFavoriteScreenModel.addRemoveManga(it, haptic) },
            onSourceClick = {
                if (successState.source !is StubSource) {
                    val screen = when {
                        // Clicked on source of an entry being merged with previous entry or
                        // source of an recommending entry (to search again)
                        smartSearchConfig != null -> SmartSearchScreen(successState.source.id, smartSearchConfig)
                        screenModel.useNewSourceNavigation -> SourceFeedScreen(successState.source.id)
                        else -> BrowseSourceScreen(successState.source.id, GetRemoteManga.QUERY_POPULAR)
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
            is MangaScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is MangaScreenModel.Dialog.DeleteChapters -> {
                DeleteChaptersDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteChapters(dialog.chapters)
                    },
                )
            }

            is MangaScreenModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { screenModel.showMigrateDialog(it) },
                )
            }

            is MangaScreenModel.Dialog.Migrate -> {
                MigrateDialog(
                    oldManga = dialog.oldManga,
                    newManga = dialog.newManga,
                    screenModel = MigrateDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(MangaScreen(dialog.oldManga.id)) },
                    onPopScreen = onDismissRequest,
                )
            }
            MangaScreenModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
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
            MangaScreenModel.Dialog.TrackSheet -> {
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
            MangaScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { MangaCoverScreenModel(successState.manga.id) }
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
                    MangaCoverDialog(
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
                            .hazeEffect(
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
            is MangaScreenModel.Dialog.SetFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.manga, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
            // SY -->
            is MangaScreenModel.Dialog.EditMangaInfo -> {
                EditMangaDialog(
                    manga = dialog.manga,
                    // KMK -->
                    coverRatio = coverRatio,
                    // KMK <--
                    onDismissRequest = screenModel::dismissDialog,
                    onPositiveClick = screenModel::updateMangaInfo,
                )
            }

            is MangaScreenModel.Dialog.EditMergedSettings -> {
                EditMergedSettingsDialog(
                    mergedData = dialog.mergedData,
                    onDismissRequest = screenModel::dismissDialog,
                    onDeleteClick = screenModel::deleteMerge,
                    onPositiveClick = screenModel::updateMergeSettings,
                    // KMK -->
                    onOpenEntryClick = { merge ->
                        merge.mangaId?.let { navigator.push(MangaScreen(it)) }
                    },
                    // KMK <--
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

    private fun continueReading(context: Context, unreadChapter: Chapter?) {
        if (unreadChapter != null) openChapter(context, unreadChapter)
    }

    private fun openChapter(context: Context, chapter: Chapter) {
        context.startActivity(ReaderActivity.newIntent(context, mangaId, chapter.id))
    }

    @Suppress("LocalVariableName")
    private fun getMangaUrl(manga_: Manga?, source_: Source?): String? {
        val manga = manga_ ?: return null
        val source = source_ as? HttpSource ?: return null

        return try {
            source.getMangaUrl(manga.toSManga())
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("LocalVariableName")
    private fun openMangaInWebView(navigator: Navigator, manga_: Manga?, source_: Source?) {
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
    private fun shareManga(context: Context, manga_: Manga?, source_: Source?) {
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
    private suspend fun performGenreSearch(navigator: Navigator, genreName: String, source: Source) {
        if (navigator.size < 2) {
            return
        }

        var previousController: cafe.adriel.voyager.core.screen.Screen
        var idx = navigator.size - 2
        while (idx >= 0) {
            previousController = navigator.items[idx--]
            if (previousController is BrowseSourceScreen && source is HttpSource) {
                // KMK -->
                // navigator.pop()
                navigator.popUntil { navigator.size == idx + 2 }
                // KMK <--
                previousController.searchGenre(genreName)
                return
            }
            // KMK -->
            if (previousController is SourceFeedScreen && source is HttpSource) {
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
    private fun copyMangaUrl(context: Context, manga_: Manga?, source_: Source?) {
        val manga = manga_ ?: return
        val source = source_ as? HttpSource ?: return
        val url = source.getMangaUrl(manga.toSManga())
        context.copyToClipboard(url, url)
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
                openMangaInWebView(navigator, mergedManga[index], sources[index] as? HttpSource)
            }
            .setNegativeButton(MR.strings.action_cancel.getString(context), null)
            .show()
    }

    private fun openMorePagePreviews(navigator: Navigator, manga: Manga) {
        navigator.push(PagePreviewScreen(manga.id))
    }

    private fun openPagePreview(context: Context, chapter: Chapter?, page: Int) {
        chapter ?: return
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id, page))
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
                if (navigator.lastItem !is MangaScreen) {
                    navigator push MangaScreen(mergedManga.id)
                } else {
                    // KMK <--
                    navigator replace MangaScreen(mergedManga.id)
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
    private fun openRecommends(navigator: Navigator, source: Source?, manga: Manga) {
        source ?: return
        navigator.push(RecommendsScreen(manga.id, source.id))
    }
    // AZ <--
}
