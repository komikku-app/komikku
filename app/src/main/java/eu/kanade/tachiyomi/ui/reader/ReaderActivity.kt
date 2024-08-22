package eu.kanade.tachiyomi.ui.reader

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.transition.doOnEnd
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.hippo.unifile.UniFile
import com.materialkolor.dynamicColorScheme
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.reader.ChapterListDialog
import eu.kanade.presentation.reader.DisplayRefreshHost
import eu.kanade.presentation.reader.OrientationSelectDialog
import eu.kanade.presentation.reader.PageIndicatorText
import eu.kanade.presentation.reader.ReaderContentOverlay
import eu.kanade.presentation.reader.ReaderPageActionsDialog
import eu.kanade.presentation.reader.ReadingModeSelectDialog
import eu.kanade.presentation.reader.appbars.NavBarType
import eu.kanade.presentation.reader.appbars.ReaderAppBars
import eu.kanade.presentation.reader.settings.ReaderSettingsDialog
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.VerticalPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import eu.kanade.tachiyomi.util.system.isNightMode
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import exh.source.isEhBasedSource
import exh.ui.ifSourcesLoaded
import exh.util.defaultReaderType
import exh.util.mangaType
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.graphics.Color as ComposeColor

class ReaderActivity : BaseActivity() {

    companion object {

        fun newIntent(context: Context, mangaId: Long?, chapterId: Long?/* SY --> */, page: Int? = null/* SY <-- */): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                // SY -->
                putExtra("page", page)
                // SY <--
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        const val SHIFT_DOUBLE_PAGES = "shiftingDoublePages"
        const val SHIFTED_PAGE_INDEX = "shiftedPageIndex"
        const val SHIFTED_CHAP_INDEX = "shiftedChapterIndex"
    }

    private val readerPreferences = Injekt.get<ReaderPreferences>()
    private val preferences = Injekt.get<BasePreferences>()

    // KMK -->
    private val uiPreferences = Injekt.get<UiPreferences>()
    private val themeCoverBased = uiPreferences.themeCoverBased().get()
    private val themeDarkAmoled = uiPreferences.themeDarkAmoled().get()
    private val themeCoverBasedStyle = uiPreferences.themeCoverBasedStyle().get()
    // KMK <--

    lateinit var binding: ReaderActivityBinding

    val viewModel by viewModels<ReaderViewModel>()
    private var assistUrl: String? = null

    private val hasCutout by lazy { hasDisplayCutout() }

    // SY -->
    private val sourceManager = Injekt.get<SourceManager>()
    // SY <--

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    private var menuToggleToast: Toast? = null
    private var readingModeToast: Toast? = null
    private val displayRefreshHost = DisplayRefreshHost()

    private val windowInsetsController by lazy { WindowInsetsControllerCompat(window, binding.root) }

    private var loadingIndicator: ReaderProgressIndicator? = null

    var isScrollingThroughPages = false
        private set

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        registerSecureActivity(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }

        super.onCreate(savedInstanceState)

        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (viewModel.needsInit()) {
            val manga = intent.extras?.getLong("manga", -1) ?: -1L
            val chapter = intent.extras?.getLong("chapter", -1) ?: -1L
            // SY -->
            val page = intent.extras?.getInt("page", -1).takeUnless { it == -1 }
            // SY <--
            if (manga == -1L || chapter == -1L) {
                finish()
                return
            }
            NotificationReceiver.dismissNotification(this, manga.hashCode(), Notifications.ID_NEW_CHAPTERS)

            lifecycleScope.launchNonCancellable {
                val initResult = viewModel.init(manga, chapter/* SY --> */, page/* SY <-- */)
                if (!initResult.getOrDefault(false)) {
                    val exception = initResult.exceptionOrNull() ?: IllegalStateException("Unknown err")
                    withUIContext {
                        setInitialChapterError(exception)
                    }
                }
            }
        }

        config = ReaderConfig()
        initializeMenu()

        // Finish when incognito mode is disabled
        preferences.incognitoMode().changes()
            .drop(1)
            .onEach { if (!it) finish() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.isLoadingAdjacentChapter }
            .distinctUntilChanged()
            .onEach(::setProgressDialog)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { updateViewer() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderViewModel.Event.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }
                    ReaderViewModel.Event.PageChanged -> {
                        displayRefreshHost.flash()
                    }
                    is ReaderViewModel.Event.SetOrientation -> {
                        setOrientation(event.orientation)
                    }
                    is ReaderViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.page /* SY --> */, event.secondPage /* SY <-- */)
                    }
                    is ReaderViewModel.Event.CopyImage -> {
                        onCopyImageResult(event.uri)
                    }
                    is ReaderViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    /**
     * Called when the activity is destroyed. Cleans up the viewer, configuration and any view.
     */
    override fun onDestroy() {
        super.onDestroy()
        viewModel.state.value.viewer?.destroy()
        config = null
        menuToggleToast?.cancel()
        readingModeToast?.cancel()
    }

    override fun onPause() {
        viewModel.flushReadTimer()
        super.onPause()
    }

    /**
     * Set menu visibility again on activity resume to apply immersive mode again if needed.
     * Helps with rotations.
     */
    override fun onResume() {
        super.onResume()
        viewModel.restartReadTimer()
        setMenuVisibility(viewModel.state.value.menuVisible)
    }

    /**
     * Called when the window focus changes. It sets the menu visibility to the last known state
     * to apply immersive mode again if needed.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setMenuVisibility(viewModel.state.value.menuVisible)
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    /**
     * Called when the user clicks the back key or the button on the toolbar. The call is
     * delegated to the presenter.
     */
    override fun finish() {
        viewModel.onActivityFinish()
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_N) {
            loadNextChapter()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_P) {
            loadPreviousChapter()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Dispatches a key event. If the viewer doesn't handle it, call the default implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = viewModel.state.value.viewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    /**
     * Dispatches a generic motion event. If the viewer doesn't handle it, call the default
     * implementation.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = viewModel.state.value.viewer?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    /**
     * Initializes the reader menu. It sets up click listeners and the initial visibility.
     */
    private fun initializeMenu() {
        // KMK -->
        @Composable
        fun pageNumberContent() {
            // KMK <--
            val state by viewModel.state.collectAsState()
            val showPageNumber by viewModel.readerPreferences.showPageNumber().collectAsState()

            if (!state.menuVisible && showPageNumber) {
                PageIndicatorText(
                    // SY -->
                    currentPage = state.currentPageText,
                    // SY <--
                    totalPages = state.totalPages,
                )
            }
        }

        // KMK -->
        binding.pageNumber.setComposeContent {
            TachiyomiTheme(
                seedColor = seedColorState().takeIf { themeCoverBased },
            ) {
                pageNumberContent()
            }
        }

        @Composable
        fun dialogRootContent() {
            val context = LocalContext.current
            // KMK <--
            val state by viewModel.state.collectAsState()
            val settingsScreenModel = remember {
                ReaderSettingsScreenModel(
                    readerState = viewModel.state,
                    hasDisplayCutout = hasCutout,
                    onChangeReadingMode = viewModel::setMangaReadingMode,
                    onChangeOrientation = viewModel::setMangaOrientationType,
                )
            }

            if (!ifSourcesLoaded()) {
                return
            }

            val isHttpSource = viewModel.getSource() is HttpSource
            val isFullscreen by readerPreferences.fullscreen().collectAsState()
            val flashOnPageChange by readerPreferences.flashOnPageChange().collectAsState()

            val colorOverlayEnabled by readerPreferences.colorFilter().collectAsState()
            val colorOverlay by readerPreferences.colorFilterValue().collectAsState()
            val colorOverlayMode by readerPreferences.colorFilterMode().collectAsState()
            val colorOverlayBlendMode = remember(colorOverlayMode) {
                ReaderPreferences.ColorFilterMode.getOrNull(colorOverlayMode)?.second
            }

            val cropBorderPaged by readerPreferences.cropBorders().collectAsState()
            val cropBorderWebtoon by readerPreferences.cropBordersWebtoon().collectAsState()
            // SY -->
            val readingMode = viewModel.getMangaReadingMode()
            val isPagerType = ReadingMode.isPagerType(readingMode)
            val isWebtoon = ReadingMode.WEBTOON.flagValue == readingMode
            val cropBorderContinuousVertical by readerPreferences.cropBordersContinuousVertical().collectAsState()
            val cropEnabled = if (isPagerType) {
                cropBorderPaged
            } else if (isWebtoon) {
                cropBorderWebtoon
            } else {
                cropBorderContinuousVertical
            }
            val readerBottomButtons by readerPreferences.readerBottomButtons().changes().map { it.toImmutableSet() }
                .collectAsState(persistentSetOf())
            val dualPageSplitPaged by readerPreferences.dualPageSplitPaged().collectAsState()

            val forceHorizontalSeekbar by readerPreferences.forceHorizontalSeekbar().collectAsState()
            val landscapeVerticalSeekbar by readerPreferences.landscapeVerticalSeekbar().collectAsState()
            val leftHandedVerticalSeekbar by readerPreferences.leftVerticalSeekbar().collectAsState()
            val configuration = LocalConfiguration.current
            val verticalSeekbarLandscape =
                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && landscapeVerticalSeekbar
            val verticalSeekbarHorizontal = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val viewerIsVertical = (state.viewer is WebtoonViewer || state.viewer is VerticalPagerViewer)
            val showVerticalSeekbar =
                !forceHorizontalSeekbar && (verticalSeekbarLandscape || verticalSeekbarHorizontal) && viewerIsVertical
            val navBarType = when {
                !showVerticalSeekbar -> NavBarType.Bottom
                leftHandedVerticalSeekbar -> NavBarType.VerticalLeft
                else -> NavBarType.VerticalRight
            }
            // SY <--

            // KMK -->
            val externalStoragePermissionNotGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_DENIED
            val permissionRequester = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = {
                    toast(KMR.strings.permission_writing_external_storage_succeed)
                },
            )
            // KMK <--

            ReaderContentOverlay(
                brightness = state.brightnessOverlayValue,
                color = colorOverlay.takeIf { colorOverlayEnabled },
                colorBlendMode = colorOverlayBlendMode,
            )

            ReaderAppBars(
                visible = state.menuVisible,
                fullscreen = isFullscreen,

                mangaTitle = state.manga?.title,
                chapterTitle = state.currentChapter?.chapter?.name,
                navigateUp = onBackPressedDispatcher::onBackPressed,
                onClickTopAppBar = ::openMangaScreen,
                // bookmarked = state.bookmarked,
                // onToggleBookmarked = viewModel::toggleChapterBookmark,
                onOpenInBrowser = ::openChapterInBrowser.takeIf { isHttpSource },
                onOpenInWebView = ::openChapterInWebView.takeIf { isHttpSource },
                onShare = ::shareChapter.takeIf { isHttpSource },

                viewer = state.viewer,
                onNextChapter = ::loadNextChapter,
                enabledNext = state.viewerChapters?.nextChapter != null,
                onPreviousChapter = ::loadPreviousChapter,
                enabledPrevious = state.viewerChapters?.prevChapter != null,
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                onSliderValueChange = {
                    isScrollingThroughPages = true
                    moveToPageIndex(it)
                },

                readingMode = ReadingMode.fromPreference(
                    viewModel.getMangaReadingMode(resolveDefault = false),
                ),
                onClickReadingMode = viewModel::openReadingModeSelectDialog,
                orientation = ReaderOrientation.fromPreference(
                    viewModel.getMangaOrientation(resolveDefault = false),
                ),
                onClickOrientation = viewModel::openOrientationModeSelectDialog,
                cropEnabled = cropEnabled,
                onClickCropBorder = {
                    val enabled = viewModel.toggleCropBorders()
                    menuToggleToast?.cancel()
                    menuToggleToast = toast(if (enabled) MR.strings.on else MR.strings.off)
                },
                onClickSettings = viewModel::openSettingsDialog,
                // SY -->
                isExhToolsVisible = state.ehUtilsVisible,
                onSetExhUtilsVisibility = viewModel::showEhUtils,
                isAutoScroll = state.autoScroll,
                isAutoScrollEnabled = state.isAutoScrollEnabled,
                onToggleAutoscroll = viewModel::toggleAutoScroll,
                autoScrollFrequency = state.ehAutoscrollFreq,
                onSetAutoScrollFrequency = viewModel::setAutoScrollFrequency,
                onClickAutoScrollHelp = viewModel::openAutoScrollHelpDialog,
                onClickRetryAll = ::exhRetryAll,
                onClickRetryAllHelp = viewModel::openRetryAllHelp,
                onClickBoostPage = ::exhBoostPage,
                onClickBoostPageHelp = viewModel::openBoostPageHelp,
                currentPageText = state.currentPageText,
                navBarType = navBarType,
                enabledButtons = readerBottomButtons,
                currentReadingMode = ReadingMode.fromPreference(
                    viewModel.getMangaReadingMode(resolveDefault = true),
                ),
                dualPageSplitEnabled = dualPageSplitPaged,
                doublePages = state.doublePages,
                onClickChapterList = viewModel::openChapterListDialog,
                onClickPageLayout = {
                    if (readerPreferences.pageLayout().get() == PagerConfig.PageLayout.AUTOMATIC) {
                        (viewModel.state.value.viewer as? PagerViewer)?.config?.let { config ->
                            config.doublePages = !config.doublePages
                            reloadChapters(config.doublePages, true)
                        }
                    } else {
                        readerPreferences.pageLayout().set(1 - readerPreferences.pageLayout().get())
                    }
                },
                onClickShiftPage = ::shiftDoublePages,
                // SY <--
            )

            if (flashOnPageChange) {
                DisplayRefreshHost(
                    hostState = displayRefreshHost,
                )
            }

            val onDismissRequest = viewModel::closeDialog
            when (state.dialog) {
                is ReaderViewModel.Dialog.Loading -> {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    // KMK -->
                                    color = MaterialTheme.colorScheme.primary,
                                    // KMK <--
                                )
                                Text(stringResource(MR.strings.loading))
                            }
                        },
                    )
                }
                is ReaderViewModel.Dialog.Settings -> {
                    ReaderSettingsDialog(
                        onDismissRequest = onDismissRequest,
                        onShowMenus = { setMenuVisibility(true) },
                        onHideMenus = { setMenuVisibility(false) },
                        screenModel = settingsScreenModel,
                    )
                }
                is ReaderViewModel.Dialog.ReadingModeSelect -> {
                    ReadingModeSelectDialog(
                        onDismissRequest = onDismissRequest,
                        screenModel = settingsScreenModel,
                        onChange = { stringRes ->
                            menuToggleToast?.cancel()
                            if (!readerPreferences.showReadingMode().get()) {
                                menuToggleToast = toast(stringRes)
                            }
                        },
                    )
                }
                is ReaderViewModel.Dialog.OrientationModeSelect -> {
                    OrientationSelectDialog(
                        onDismissRequest = onDismissRequest,
                        screenModel = settingsScreenModel,
                        onChange = { stringRes ->
                            menuToggleToast?.cancel()
                            menuToggleToast = toast(stringRes)
                        },
                    )
                }
                is ReaderViewModel.Dialog.PageActions -> {
                    ReaderPageActionsDialog(
                        onDismissRequest = onDismissRequest,
                        onSetAsCover = viewModel::setAsCover,
                        onShare = viewModel::shareImage,
                        onSave = { extra ->
                            // KMK -->
                            if (externalStoragePermissionNotGranted) {
                                permissionRequester.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                // KMK <--
                                viewModel.saveImage(extra)
                            }
                        },
                        onShareCombined = viewModel::shareImages,
                        onSaveCombined = {
                            // KMK -->
                            if (externalStoragePermissionNotGranted) {
                                permissionRequester.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                // KMK <--
                                viewModel.saveImages()
                            }
                        },
                        hasExtraPage = (state.dialog as? ReaderViewModel.Dialog.PageActions)?.extraPage != null,
                    )
                }
                is ReaderViewModel.Dialog.ChapterList -> {
                    var chapters by remember {
                        mutableStateOf(viewModel.getChapters().toImmutableList())
                    }
                    ChapterListDialog(
                        onDismissRequest = onDismissRequest,
                        screenModel = settingsScreenModel,
                        chapters = chapters,
                        onClickChapter = {
                            viewModel.loadNewChapterFromDialog(it)
                            onDismissRequest()
                        },
                        onBookmark = { chapter ->
                            viewModel.toggleBookmark(chapter.id, !chapter.bookmark)
                            chapters = chapters.map {
                                if (it.chapter.id == chapter.id) {
                                    it.copy(chapter = chapter.copy(bookmark = !chapter.bookmark))
                                } else {
                                    it
                                }
                            }.toImmutableList()
                        },
                        state.dateRelativeTime,
                    )
                }
                // SY -->
                ReaderViewModel.Dialog.AutoScrollHelp -> AlertDialog(
                    onDismissRequest = onDismissRequest,
                    confirmButton = {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = stringResource(MR.strings.action_ok))
                        }
                    },
                    title = { Text(text = stringResource(SYMR.strings.eh_autoscroll_help)) },
                    text = { Text(text = stringResource(SYMR.strings.eh_autoscroll_help_message)) },
                )
                ReaderViewModel.Dialog.BoostPageHelp -> AlertDialog(
                    onDismissRequest = onDismissRequest,
                    confirmButton = {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = stringResource(MR.strings.action_ok))
                        }
                    },
                    title = { Text(text = stringResource(SYMR.strings.eh_boost_page_help)) },
                    text = { Text(text = stringResource(SYMR.strings.eh_boost_page_help_message)) },
                )
                ReaderViewModel.Dialog.RetryAllHelp -> AlertDialog(
                    onDismissRequest = onDismissRequest,
                    confirmButton = {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = stringResource(MR.strings.action_ok))
                        }
                    },
                    title = { Text(text = stringResource(SYMR.strings.eh_retry_all_help)) },
                    text = { Text(text = stringResource(SYMR.strings.eh_retry_all_help_message)) },
                )
                // SY <--
                null -> {}
            }
        }

        // KMK -->
        binding.dialogRoot.setComposeContent {
            TachiyomiTheme(
                seedColor = seedColorState().takeIf { themeCoverBased },
            ) {
                dialogRootContent()
            }
        }

        val colorScheme = seedColorStatic()?.let {
            dynamicColorScheme(
                seedColor = it,
                isDark = isNightMode(),
                isAmoled = themeDarkAmoled,
                style = themeCoverBasedStyle,
            )
        }
        // KMK <--

        val toolbarColor = ColorUtils.setAlphaComponent(
            // KMK -->
            if (themeCoverBased && colorScheme != null) {
                colorScheme.surfaceColorAtElevation(3.dp).toArgb()
            } else {
                // KMK <--
                SurfaceColors.SURFACE_2.getColor(this)
            },
            if (isNightMode()) 230 else 242, // 90% dark 95% light
        )
        window.statusBarColor = toolbarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.navigationBarColor = toolbarColor
        }

        // Set initial visibility
        setMenuVisibility(viewModel.state.value.menuVisible)

        enableExhAutoScroll()
    }

    // KMK -->
    @Composable
    private fun seedColorState(): ComposeColor? {
        val state by viewModel.state.collectAsState()
        return state.manga?.asMangaCover()?.vibrantCoverColor?.let { ComposeColor(it) }
            ?: seedColorStatic()
    }

    private fun seedColorStatic(): ComposeColor? {
        return viewModel.manga?.asMangaCover()?.vibrantCoverColor?.let { ComposeColor(it) }
            ?: intent.extras?.getLong("manga")?.takeIf { it > 0 }
                ?.let { MangaCover.vibrantCoverColorMap[it] }
                ?.let { ComposeColor(it) }
    }
    // KMK <--

    private fun enableExhAutoScroll() {
        readerPreferences.autoscrollInterval().changes()
            .combine(viewModel.state.map { it.autoScroll }.distinctUntilChanged()) { interval, enabled ->
                interval.toDouble() to enabled
            }.mapLatest { (intervalFloat, enabled) ->
                if (enabled) {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        val interval = intervalFloat.seconds
                        while (true) {
                            if (!viewModel.state.value.menuVisible) {
                                viewModel.state.value.viewer.let { v ->
                                    when (v) {
                                        is PagerViewer -> v.moveToNext()
                                        is WebtoonViewer -> {
                                            if (readerPreferences.smoothAutoScroll().get()) {
                                                v.linearScroll(interval)
                                            } else {
                                                v.scrollDown()
                                            }
                                        }
                                    }
                                }
                                delay(interval)
                            } else {
                                delay(100)
                            }
                        }
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun exhRetryAll() {
        var retried = 0

        viewModel.state.value.viewerChapters
            ?.currChapter
            ?.pages
            ?.forEachIndexed { _, page ->
                var shouldQueuePage = false
                if (page.status == Page.State.ERROR) {
                    shouldQueuePage = true
                } /*else if (page.status == Page.LOAD_PAGE ||
                                    page.status == Page.DOWNLOAD_IMAGE) {
                                // Do nothing
                            }*/

                if (shouldQueuePage) {
                    page.status = Page.State.QUEUE
                } else {
                    return@forEachIndexed
                }

                // If we are using EHentai/ExHentai, get a new image URL
                viewModel.manga?.let { m ->
                    val src = sourceManager.get(m.source)
                    if (src?.isEhBasedSource() == true) {
                        page.imageUrl = null
                    }
                }

                val loader = page.chapter.pageLoader
                if (page.index == exhCurrentpage()?.index && loader is HttpPageLoader) {
                    loader.boostPage(page)
                } else {
                    loader?.retryPage(page)
                }

                retried++
            }

        toast(pluralStringResource(SYMR.plurals.eh_retry_toast, retried, retried))
    }

    private fun exhBoostPage() {
        viewModel.state.value.viewer ?: return
        val curPage = exhCurrentpage() ?: run {
            toast(SYMR.strings.eh_boost_page_invalid)
            return
        }

        if (curPage.status == Page.State.ERROR) {
            toast(SYMR.strings.eh_boost_page_errored)
        } else if (curPage.status == Page.State.LOAD_PAGE || curPage.status == Page.State.DOWNLOAD_IMAGE) {
            toast(SYMR.strings.eh_boost_page_downloading)
        } else if (curPage.status == Page.State.READY) {
            toast(SYMR.strings.eh_boost_page_downloaded)
        } else {
            val loader = (viewModel.state.value.viewerChapters?.currChapter?.pageLoader as? HttpPageLoader)
            if (loader != null) {
                loader.boostPage(curPage)
                toast(SYMR.strings.eh_boost_boosted)
            } else {
                toast(SYMR.strings.eh_boost_invalid_loader)
            }
        }
    }

    private fun exhCurrentpage(): ReaderPage? {
        val viewer = viewModel.state.value.viewer
        val currentPage = (((viewer as? PagerViewer)?.currentPage ?: (viewer as? WebtoonViewer)?.currentPage) as? ReaderPage)?.index
        return currentPage?.let { viewModel.state.value.viewerChapters?.currChapter?.pages?.getOrNull(it) }
    }

    fun reloadChapters(doublePages: Boolean, force: Boolean = false) {
        val viewer = viewModel.state.value.viewer as? PagerViewer ?: return
        viewer.updateShifting()
        if (!force && viewer.config.autoDoublePages) {
            setDoublePageMode(viewer)
        } else {
            viewer.config.doublePages = doublePages
            viewModel.setDoublePages(viewer.config.doublePages)
        }
        val currentChapter = viewModel.state.value.currentChapter
        if (doublePages) {
            // If we're moving from singe to double, we want the current page to be the first page
            val currentPage = viewModel.state.value.currentPage
            viewer.config.shiftDoublePage = (
                currentPage + (currentChapter?.pages?.take(currentPage)?.count { it.fullPage || it.isolatedPage } ?: 0)
                ) % 2 != 0
        }
        viewModel.state.value.viewerChapters?.let {
            viewer.setChaptersDoubleShift(it)
        }
    }

    private fun setDoublePageMode(viewer: PagerViewer) {
        val currentOrientation = resources.configuration.orientation
        viewer.config.doublePages = currentOrientation == Configuration.ORIENTATION_LANDSCAPE
        viewModel.setDoublePages(viewer.config.doublePages)
    }

    private fun shiftDoublePages() {
        val viewer = viewModel.state.value.viewer as? PagerViewer ?: return
        viewer.config.let { config ->
            config.shiftDoublePage = !config.shiftDoublePage
            viewModel.state.value.viewerChapters?.let {
                viewer.updateShifting()
                viewer.setChaptersDoubleShift(it)
                invalidateOptionsMenu()
            }
        }
    }
    // EXH <--

    /**
     * Sets the visibility of the menu according to [visible].
     */
    private fun setMenuVisibility(visible: Boolean) {
        viewModel.showMenus(visible)
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        } else {
            if (readerPreferences.fullscreen().get()) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    /**
     * Called from the presenter when a manga is ready. Used to instantiate the appropriate viewer.
     */
    private fun updateViewer() {
        val prevViewer = viewModel.state.value.viewer
        val newViewer = ReadingMode.toViewer(
            viewModel.getMangaReadingMode(),
            this,
            // KMK -->
            seedColor = seedColorStatic()?.toArgb(),
            // KMK <--
        )

        if (window.sharedElementEnterTransition is MaterialContainerTransform) {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.doOnEnd {
                setOrientation(viewModel.getMangaOrientation())
            }
        } else {
            setOrientation(viewModel.getMangaOrientation())
        }

        // Destroy previous viewer if there was one
        if (prevViewer != null) {
            prevViewer.destroy()
            binding.viewerContainer.removeAllViews()
        }
        viewModel.onViewerLoaded(newViewer)
        updateViewerInset(readerPreferences.fullscreen().get())
        binding.viewerContainer.addView(newViewer.getView())

        // SY -->
        if (newViewer is PagerViewer) {
            if (readerPreferences.pageLayout().get() == PagerConfig.PageLayout.AUTOMATIC) {
                setDoublePageMode(newViewer)
            }
            viewModel.state.value.lastShiftDoubleState?.let { newViewer.config.shiftDoublePage = it }
        }

        val manga = viewModel.state.value.manga
        val defaultReaderType = manga?.defaultReaderType(
            manga.mangaType(sourceName = sourceManager.get(manga.source)?.name),
        )
        if (
            readerPreferences.useAutoWebtoon().get() &&
            (manga?.readingMode?.toInt() ?: ReadingMode.DEFAULT.flagValue) == ReadingMode.DEFAULT.flagValue &&
            defaultReaderType != null &&
            defaultReaderType == ReadingMode.WEBTOON.flagValue
        ) {
            readingModeToast?.cancel()
            readingModeToast = toast(SYMR.strings.eh_auto_webtoon_snack)
        } else if (readerPreferences.showReadingMode().get()) {
            // SY <--
            showReadingModeToast(viewModel.getMangaReadingMode())
        }

        loadingIndicator = ReaderProgressIndicator(
            context = this,
            // KMK -->
            seedColor = seedColorStatic()?.toArgb(),
            // KMK <--
        )
        binding.readerContainer.addView(loadingIndicator)

        startPostponedEnterTransition()
    }

    private fun openMangaScreen() {
        viewModel.manga?.id?.let { id ->
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = Constants.SHORTCUT_MANGA
                    putExtra(Constants.MANGA_EXTRA, id)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }
    }

    private fun openChapterInBrowser() {
        assistUrl?.let {
            openInBrowser(it.toUri(), forceDefaultBrowser = false)
        }
    }

    private fun openChapterInWebView() {
        val manga = viewModel.manga ?: return
        val source = viewModel.getSource() ?: return
        assistUrl?.let {
            val intent = WebViewActivity.newIntent(this@ReaderActivity, it, source.id, manga.title)
            startActivity(intent)
        }
    }

    private fun shareChapter() {
        assistUrl?.let {
            val intent = it.toUri().toShareIntent(this, type = "text/plain")
            startActivity(Intent.createChooser(intent, stringResource(MR.strings.action_share)))
        }
    }

    private fun showReadingModeToast(mode: Int) {
        try {
            readingModeToast?.cancel()
            readingModeToast = toast(ReadingMode.fromPreference(mode).stringRes)
        } catch (e: ArrayIndexOutOfBoundsException) {
            logcat(LogPriority.ERROR) { "Unknown reading mode: $mode" }
        }
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the toolbar, and
     * hides or disables the reader prev/next buttons if there's a prev or next chapter
     */
    @SuppressLint("RestrictedApi")
    private fun setChapters(viewerChapters: ViewerChapters) {
        binding.readerContainer.removeView(loadingIndicator)
        // SY -->
        val state = viewModel.state.value
        if (state.indexChapterToShift != null && state.indexPageToShift != null) {
            viewerChapters.currChapter.pages?.find {
                it.index == state.indexPageToShift && it.chapter.chapter.id == state.indexChapterToShift
            }?.let {
                (viewModel.state.value.viewer as? PagerViewer)?.updateShifting(it)
            }
            viewModel.setIndexChapterToShift(null)
            viewModel.setIndexPageToShift(null)
        } else if (state.lastShiftDoubleState != null) {
            val currentChapter = viewerChapters.currChapter
            (viewModel.state.value.viewer as? PagerViewer)?.config?.shiftDoublePage = (
                currentChapter.requestedPage +
                    (
                        currentChapter.pages?.take(currentChapter.requestedPage)
                            ?.count { it.fullPage || it.isolatedPage } ?: 0
                        )
                ) % 2 != 0
        }
        // SY <--

        viewModel.state.value.viewer?.setChapters(viewerChapters)

        lifecycleScope.launchIO {
            viewModel.getChapterUrl()?.let { url ->
                assistUrl = url
            }
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        finish()
        toast(error.message)
    }

    /**
     * Called from the presenter whenever it's loading the next or previous chapter. It shows or
     * dismisses a non-cancellable dialog to prevent user interaction according to the value of
     * [show]. This is only used when the next/previous buttons on the toolbar are clicked; the
     * other cases are handled with chapter transitions on the viewers and chapter preloading.
     */
    private fun setProgressDialog(show: Boolean) {
        if (show) {
            viewModel.showLoadingDialog()
        } else {
            viewModel.closeDialog()
        }
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    private fun moveToPageIndex(index: Int) {
        val viewer = viewModel.state.value.viewer ?: return
        val currentChapter = viewModel.state.value.currentChapter ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page)
    }

    /**
     * Tells the presenter to load the next chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadNextChapter() {
        lifecycleScope.launch {
            viewModel.loadNextChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Tells the presenter to load the previous chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadPreviousChapter() {
        lifecycleScope.launch {
            viewModel.loadPreviousChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    @SuppressLint("SetTextI18n")
    fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean = false) {
        // SY -->
        val currentPageText = if (hasExtraPage) {
            val invertDoublePage = (viewModel.state.value.viewer as? PagerViewer)?.config?.invertDoublePages ?: false
            if ((resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) xor
                invertDoublePage
            ) {
                "${page.number}-${page.number + 1}"
            } else {
                "${page.number + 1}-${page.number}"
            }
        } else {
            "${page.number}"
        }
        viewModel.onPageSelected(page, currentPageText, hasExtraPage)
        // SY <--
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(page: ReaderPage, extraPage: ReaderPage? = null) {
        // SY -->
        viewModel.openPageDialog(page, extraPage)
        // SY <--
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        lifecycleScope.launchIO { viewModel.preload(chapter) }
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
        setMenuVisibility(!viewModel.state.value.menuVisible)
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (!viewModel.state.value.menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the viewer to hide the menu.
     */
    fun hideMenu() {
        if (viewModel.state.value.menuVisible) {
            setMenuVisibility(false)
        }
    }

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    fun onShareImageResult(uri: Uri, page: ReaderPage /* SY --> */, secondPage: ReaderPage? = null /* SY <-- */) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        // SY -->
        val text = if (secondPage != null) {
            stringResource(
                SYMR.strings.share_pages_info, manga.title, chapter.name,
                if (resources.configuration.layoutDirection ==
                    View.LAYOUT_DIRECTION_LTR
                ) {
                    "${page.number}-${page.number + 1}"
                } else {
                    "${page.number + 1}-${page.number}"
                },
            )
        } else {
            stringResource(MR.strings.share_page_info, manga.title, chapter.name, page.number)
        }
        // SY <--

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = /* SY --> */ text, // SY <--
        )
        startActivity(Intent.createChooser(intent, stringResource(MR.strings.action_share)))
    }

    private fun onCopyImageResult(uri: Uri) {
        val clipboardManager = applicationContext.getSystemService<ClipboardManager>() ?: return
        val clipData = ClipData.newUri(applicationContext.contentResolver, "", uri)
        clipboardManager.setPrimaryClip(clipData)
    }

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is ReaderViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> MR.strings.cover_updated
                AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    private fun setOrientation(orientation: Int) {
        val newOrientation = ReaderOrientation.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
    }

    /**
     * Updates viewer inset depending on fullscreen reader preferences.
     */
    private fun updateViewerInset(fullscreen: Boolean) {
        viewModel.state.value.viewer?.getView()?.applyInsetter {
            if (!fullscreen) {
                type(navigationBars = true, statusBars = true) {
                    padding()
                }
            }
        }
    }

    /**
     * Class that handles the user preferences of the reader.
     */
    private inner class ReaderConfig {

        private fun getCombinedPaint(grayscale: Boolean, invertedColors: Boolean): Paint {
            return Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        if (grayscale) {
                            setSaturation(0f)
                        }
                        if (invertedColors) {
                            postConcat(
                                ColorMatrix(
                                    floatArrayOf(
                                        -1f, 0f, 0f, 0f, 255f,
                                        0f, -1f, 0f, 0f, 255f,
                                        0f, 0f, -1f, 0f, 255f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
        }

        private val grayBackgroundColor = Color.rgb(0x20, 0x21, 0x25)

        /**
         * Initializes the reader subscriptions.
         */
        init {
            readerPreferences.readerTheme().changes()
                .onEach { theme ->
                    binding.readerContainer.setBackgroundColor(
                        when (theme) {
                            0 -> Color.WHITE
                            2 -> grayBackgroundColor
                            3 -> automaticBackgroundColor()
                            else -> Color.BLACK
                        },
                    )
                }
                .launchIn(lifecycleScope)

            preferences.displayProfile().changes()
                .onEach { setDisplayProfile(it) }
                .launchIn(lifecycleScope)

            readerPreferences.cutoutShort().changes()
                .onEach(::setCutoutShort)
                .launchIn(lifecycleScope)

            readerPreferences.keepScreenOn().changes()
                .onEach(::setKeepScreenOn)
                .launchIn(lifecycleScope)

            readerPreferences.customBrightness().changes()
                .onEach(::setCustomBrightness)
                .launchIn(lifecycleScope)

            merge(readerPreferences.grayscale().changes(), readerPreferences.invertedColors().changes())
                .onEach { setLayerPaint(readerPreferences.grayscale().get(), readerPreferences.invertedColors().get()) }
                .launchIn(lifecycleScope)

            readerPreferences.fullscreen().changes()
                .onEach {
                    WindowCompat.setDecorFitsSystemWindows(window, !it)
                    updateViewerInset(it)
                }
                .launchIn(lifecycleScope)

            // SY -->
            readerPreferences.pageLayout().changes()
                .drop(1)
                .onEach {
                    viewModel.setDoublePages(
                        (viewModel.state.value.viewer as? PagerViewer)
                            ?.config
                            ?.doublePages
                            ?: false,
                    )
                }
                .launchIn(lifecycleScope)

            readerPreferences.dualPageSplitPaged().changes()
                .drop(1)
                .onEach {
                    if (viewModel.state.value.viewer !is PagerViewer) return@onEach
                    reloadChapters(
                        !it &&
                            when (readerPreferences.pageLayout().get()) {
                                PagerConfig.PageLayout.DOUBLE_PAGES -> true
                                PagerConfig.PageLayout.AUTOMATIC ->
                                    resources.configuration.orientation ==
                                        Configuration.ORIENTATION_LANDSCAPE
                                else -> false
                            },
                        true,
                    )
                }
                .launchIn(lifecycleScope)
            // SY <--
        }

        /**
         * Picks background color for [ReaderActivity] based on light/dark theme preference
         */
        private fun automaticBackgroundColor(): Int {
            return if (baseContext.isNightMode()) {
                grayBackgroundColor
            } else {
                Color.WHITE
            }
        }

        /**
         * Sets the display profile to [path].
         */
        private fun setDisplayProfile(path: String) {
            val file = UniFile.fromUri(baseContext, path.toUri())
            if (file != null && file.exists()) {
                val inputStream = file.openInputStream()
                val outputStream = ByteArrayOutputStream()
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                val data = outputStream.toByteArray()
                SubsamplingScaleImageView.setDisplayProfile(data)
                TachiyomiImageDecoder.displayProfile = data
            }
        }

        private fun setCutoutShort(enabled: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

            window.attributes.layoutInDisplayCutoutMode = when (enabled) {
                true -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                false -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            // Trigger relayout
            setMenuVisibility(viewModel.state.value.menuVisible)
        }

        /**
         * Sets the keep screen on mode according to [enabled].
         */
        private fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        /**
         * Sets the custom brightness overlay according to [enabled].
         */
        private fun setCustomBrightness(enabled: Boolean) {
            if (enabled) {
                readerPreferences.customBrightnessValue().changes()
                    .sample(100)
                    .onEach(::setCustomBrightnessValue)
                    .launchIn(lifecycleScope)
            } else {
                setCustomBrightnessValue(0)
            }
        }

        /**
         * Sets the brightness of the screen. Range is [-75, 100].
         * From -75 to -1 a semi-transparent black view is overlaid with the minimum brightness.
         * From 1 to 100 it sets that value as brightness.
         * 0 sets system brightness and hides the overlay.
         */
        private fun setCustomBrightnessValue(value: Int) {
            // Calculate and set reader brightness.
            val readerBrightness = when {
                value > 0 -> {
                    value / 100f
                }
                value < 0 -> {
                    0.01f
                }
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = window.attributes.apply { screenBrightness = readerBrightness }

            viewModel.setBrightnessOverlayValue(value)
        }
        private fun setLayerPaint(grayscale: Boolean, invertedColors: Boolean) {
            val paint = if (grayscale || invertedColors) getCombinedPaint(grayscale, invertedColors) else null
            binding.viewerContainer.setLayerType(LAYER_TYPE_HARDWARE, paint)
        }
    }
}
