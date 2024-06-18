package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.materialkolor.DynamicMaterialTheme
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.ui.UiPreferences
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
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.AddDuplicateMangaDialog
import eu.kanade.tachiyomi.ui.browse.AllowDuplicateDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.ChangeMangaCategoryDialog
import eu.kanade.tachiyomi.ui.browse.ChangeMangasCategoryDialog
import eu.kanade.tachiyomi.ui.browse.RemoveMangaDialog
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.merged.EditMergedSettingsDialog
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen
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
import exh.ui.ifSourcesLoaded
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
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance
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
        val screenModel =
            rememberScreenModel { MangaScreenModel(context, mangaId, fromSource, smartSearchConfig != null) }

        val state by screenModel.state.collectAsState()

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
                bulkFavoriteState.selectionMode -> bulkFavoriteScreenModel.toggleSelectionMode()
                showingRelatedMangasScreen.value -> showingRelatedMangasScreen.value = false
            }
        }

        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val seedColorState = rememberUpdatedState(newValue = successState.seedColor)

        val content = @Composable {
            val slideDistance = rememberSlideDistance()
            AnimatedContent(
                targetState = showingRelatedMangasScreen.value,
                transitionSpec = {
                    materialSharedAxisX(
                        forward = navigator.lastEvent != StackEvent.Pop,
                        slideDistance = slideDistance,
                    )
                },
                label = "manga_related_transition",
                contentKey = { "showingRelatedMangasScreen#$it" }
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

        if (uiPreferences.themeCoverBased().get()) {
            DynamicMaterialTheme(
                seedColor = seedColorState.value ?: MaterialTheme.colorScheme.primary,
                useDarkTheme = isSystemInDarkTheme(),
                withAmoled = uiPreferences.themeDarkAmoled().get(),
                style = uiPreferences.themeCoverBasedStyle().get(),
                animate = true,
                content = { content() },
            )
        } else {
            content()
        }

        // KMK -->
        when (bulkFavoriteState.dialog) {
            is BulkFavoriteScreenModel.Dialog.AddDuplicateManga ->
                AddDuplicateMangaDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.RemoveManga ->
                RemoveMangaDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.ChangeMangaCategory ->
                ChangeMangaCategoryDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.ChangeMangasCategory ->
                ChangeMangasCategoryDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.AllowDuplicate ->
                AllowDuplicateDialog(bulkFavoriteScreenModel)

            else -> {}
        }
        // KMK <--
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

        MangaScreen(
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
                if (screenModel.loggedInTrackers.isEmpty()) {
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
            onRecommendClicked = { openRecommends(context, navigator, screenModel.source?.getMainSource(), successState.manga) },
            onMergedSettingsClicked = screenModel::showEditMergedSettingsDialog,
            onMergeClicked = { openSmartSearch(navigator, successState.manga) },
            onMergeWithAnotherClicked = { mergeWithAnother(navigator, context, successState.manga, screenModel::smartSearchMerge) },
            onOpenPagePreview = { page ->
                openPagePreview(context, successState.chapters.minByOrNull { it.chapter.sourceOrder }?.chapter, page)
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
                    screenModel.screenModelScope.launchIO { screenModel.fetchRelatedMangasFromSource(onDemand = true) }
                }
                showRelatedMangasScreen()
            },
            onRelatedMangaClick = { navigator.push(MangaScreen(it.id, true)) },
            onRelatedMangaLongClick = { bulkFavoriteScreenModel.addRemoveManga(it, haptic) },
            onSourceClick = {
                if (successState.source !is StubSource) {
                    val screen = when {
                        smartSearchConfig != null -> SmartSearchScreen(successState.source.id, smartSearchConfig)
                        screenModel.useNewSourceNavigation -> SourceFeedScreen(successState.source.id)
                        else -> BrowseSourceScreen(successState.source.id, GetRemoteManga.QUERY_POPULAR)
                    }
                    navigator.push(screen)
                } else {
                    navigator.push(ExtensionsScreen(searchSource = successState.source.name))
                }
            },
            onCoverLoaded = screenModel::setPaletteColor,
            onPaletteScreenClick = { navigator.push(PaletteScreen(successState.seedColor?.toArgb())) }
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
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenManga = { navigator.push(MangaScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        // SY -->
                        migrateManga(navigator, dialog.duplicate, screenModel.manga!!.id)
                        // SY <--
                    },
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
                    MangaCoverDialog(
                        coverDataProvider = { manga!! },
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(manga) { manga!!.hasCustomCover() },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
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
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
    }

    private fun getMangaUrl(manga_: Manga?, source_: Source?): String? {
        val manga = manga_ ?: return null
        val source = source_ as? HttpSource ?: return null

        return try {
            source.getMangaUrl(manga.toSManga())
        } catch (e: Exception) {
            null
        }
    }

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
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        // KMK -->
        navigator.popUntil { screen ->
            navigator.size < 2 || screen is BrowseSourceScreen ||
                screen is HomeScreen || screen is SourceFeedScreen
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
    private fun copyMangaUrl(context: Context, manga_: Manga?, source_: Source?) {
        val manga = manga_ ?: return
        val source = source_ as? HttpSource ?: return
        val url = source.getMangaUrl(manga.toSManga())
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
        seedColor: Color? = null,
        // KMK <--
    ) {
        navigator.push(MetadataViewScreen(manga.id, manga.source, seedColor))
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
                navigator replace MangaScreen(mergedManga.id, true)
                context.toast(SYMR.strings.entry_merged)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                context.toast(context.stringResource(SYMR.strings.failed_merge, e.message.orEmpty()))
            }
        }
    }
    // EXH <--

    // AZ -->
    private fun openRecommends(context: Context, navigator: Navigator, source: Source?, manga: Manga) {
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
        } else if (source is CatalogueSource) {
            navigator.push(RecommendsScreen(manga.id, source.id))
        }
    }
    // AZ <--
}
