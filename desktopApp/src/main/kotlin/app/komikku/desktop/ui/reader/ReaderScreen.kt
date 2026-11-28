package app.komikku.desktop.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.security.DesktopAppLock
import app.komikku.shared.reader.image.PageFit
import app.komikku.shared.reader.image.PixelSize
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Toggles the main window's fullscreen placement; `null` where there is no window (tests). */
val LocalFullscreenToggle = compositionLocalOf<(() -> Unit)?> { null }

/**
 * The desktop reader (Phase 7), drawn over the whole window by `KomikkuApp` while
 * [ReaderLauncher.request] is set. Keyboard: see [ReaderKeyMap]; mouse: click zones, wheel, Ctrl+wheel
 * zoom, drag to pan, double-click zoom. The menu (top bar with title / bookmark / settings, bottom bar
 * with chapter buttons and page slider) toggles with the middle click zone, M or Enter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(request: ReaderLauncher.Request, onClose: () -> Unit) {
    val model = remember(request) { ReaderModel(request.mangaId, request.chapterId, request.page) }
    DisposableEffect(model) { onDispose { model.close() } }
    LaunchedEffect(model) { model.init() }
    val state by model.state.collectAsState()
    val preferences = remember { Injekt.get<ReaderPreferences>() }
    val display = rememberReaderDisplay(preferences)
    val zoom = remember { ReaderZoom() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menuVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    val fullscreen = LocalFullscreenToggle.current
    val focusRequester = remember { FocusRequester() }
    var viewportHeight by remember { mutableStateOf(800f) }
    var viewportWidth by remember { mutableStateOf(1200f) }
    val webtoon = state.readingMode.type == ReadingMode.ViewerType.Webtoon
    // Double pages: horizontal paged modes only; a page turn moves by the pages shown (a wide page is shown alone)
    val doublePages = !webtoon && state.readingMode.direction == ReadingMode.Direction.Horizontal &&
        display.viewer.doublePages(PixelSize(viewportWidth.toInt(), viewportHeight.toInt()))
    // The model's spreads follow the resolved layout (one rule for page turns, progress and preloading)
    LaunchedEffect(model, doublePages) { model.setDoublePages(doublePages) }

    fun perform(action: ReaderAction) {
        when (action) {
            ReaderAction.Next -> if (webtoon) scope.launch { listState.scrollFor(ReaderAction.PageDown, viewportHeight) } else model.next()
            ReaderAction.Previous -> if (webtoon) scope.launch { listState.scrollFor(ReaderAction.PageUp, viewportHeight) } else model.previous()
            // The webtoon list follows the model's jump
            ReaderAction.First -> model.moveToFirstPage()
            ReaderAction.Last -> model.moveToLastPage()
            ReaderAction.NextChapter -> model.loadNextChapter()
            ReaderAction.PreviousChapter -> model.loadPreviousChapter()
            ReaderAction.ScrollDown, ReaderAction.ScrollUp, ReaderAction.PageDown, ReaderAction.PageUp ->
                scope.launch { listState.scrollFor(action, viewportHeight) }
            ReaderAction.ZoomIn -> zoom.zoomBy(ReaderZoom.STEP)
            ReaderAction.ZoomOut -> zoom.zoomBy(1 / ReaderZoom.STEP)
            ReaderAction.ZoomReset -> zoom.reset()
            ReaderAction.ToggleFullscreen -> fullscreen?.invoke()
            ReaderAction.ToggleMenu -> menuVisible = !menuVisible
            ReaderAction.Back -> when {
                settingsVisible -> settingsVisible = false
                menuVisible -> menuVisible = false
                else -> onClose()
            }
        }
    }

    // Keep keyboard focus on the reader (chrome buttons take it when clicked and lose it when hidden; the lock screen's
    // PIN field takes it while locked).
    val appLocked by remember { Injekt.get<DesktopAppLock>().locked }.collectAsState()
    LaunchedEffect(menuVisible, settingsVisible, state.bookmarked, appLocked) {
        if (!appLocked) focusRequester.requestFocus()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(display.background)
            .testTag("reader")
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val action = ReaderKeyMap.actionFor(event.key, event.isShiftPressed, state.readingMode) ?: return@onPreviewKeyEvent false
                perform(action)
                true
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    viewportHeight = size.height
                    viewportWidth = size.width
                    drawContent()
                    display.overlays.forEach { (color, blend) -> drawRect(color, blendMode = blend) }
                },
        ) {
            when {
                state.error != null && state.items.isEmpty() -> Text(
                    text = stringResource(MR.strings.transition_pages_error, state.error?.message.orEmpty()),
                    color = display.textColor,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp).testTag("reader-error"),
                )
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                webtoon -> WebtoonViewer(state, listState, zoom, display.viewer, ::perform, model::moveToItem, model::retry)
                else -> PagedViewer(
                    state,
                    zoom,
                    // The layout as resolved here for the page turns (automatic: the window's orientation)
                    display.viewer.copy(pageLayout = if (doublePages) PagerConfig.PageLayout.DOUBLE_PAGES else PagerConfig.PageLayout.SINGLE_PAGE),
                    ::perform,
                    model::retry,
                    model::onPageSize,
                )
            }
        }

        if (preferences.showPageNumber().collectAsState().value && state.pages.isNotEmpty() && !menuVisible) {
            Text(
                text = "${state.currentPageNumber} / ${state.pages.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag("page-indicator"),
            )
        }

        AnimatedVisibility(visible = menuVisible, modifier = Modifier.align(Alignment.TopCenter)) {
            TopAppBar(
                title = {
                    Column {
                        Text(state.manga?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            state.currentChapter?.chapter?.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(MR.strings.action_bar_up_description))
                    }
                },
                actions = {
                    IconButton(onClick = model::toggleBookmark, modifier = Modifier.testTag("reader-bookmark")) {
                        Icon(
                            if (state.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(if (state.bookmarked) MR.strings.action_remove_bookmark else MR.strings.action_bookmark),
                        )
                    }
                    if (fullscreen != null) {
                        IconButton(onClick = fullscreen) {
                            Icon(Icons.Outlined.Fullscreen, contentDescription = stringResource(MR.strings.pref_fullscreen))
                        }
                    }
                    IconButton(onClick = { settingsVisible = !settingsVisible }, modifier = Modifier.testTag("reader-settings-button")) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.action_settings))
                    }
                },
            )
        }
        AnimatedVisibility(visible = menuVisible, modifier = Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomBar(state, model)
        }
        if (settingsVisible) {
            ReaderSettingsPanel(
                state = state,
                preferences = preferences,
                onReadingMode = model::setReadingMode,
                onOrientation = model::setOrientation,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(state: ReaderModel.State, model: ReaderModel) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = model::loadPreviousChapter, enabled = state.viewerChapters?.prevChapter != null) {
                Icon(Icons.Outlined.SkipPrevious, contentDescription = stringResource(MR.strings.action_previous_chapter))
            }
            val pages = state.pages.size
            if (pages > 1) {
                Text(state.currentPageNumber.toString(), modifier = Modifier.padding(horizontal = 8.dp))
                Slider(
                    value = state.currentPageNumber.toFloat(),
                    onValueChange = { model.moveToPage(it.toInt() - 1) },
                    valueRange = 1f..pages.toFloat(),
                    steps = (pages - 2).coerceAtLeast(0),
                    modifier = Modifier.weight(1f).testTag("reader-slider"),
                )
                Text(pages.toString(), modifier = Modifier.padding(horizontal = 8.dp))
            } else {
                Box(Modifier.weight(1f))
            }
            IconButton(onClick = model::loadNextChapter, enabled = state.viewerChapters?.nextChapter != null) {
                Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(MR.strings.action_next_chapter))
            }
        }
    }
}

/** Viewer options + colour overlays (custom tint, brightness) resolved from `ReaderPreferences`. */
data class ReaderDisplaySettings(
    val viewer: ReaderDisplay,
    val overlays: List<Pair<Color, BlendMode>>,
) {
    val background: Color get() = viewer.background
    val textColor: Color get() = viewer.textColor
}

@Composable
private fun rememberReaderDisplay(preferences: ReaderPreferences): ReaderDisplaySettings {
    val theme by preferences.readerTheme().collectAsState()
    val scale by preferences.imageScaleType().collectAsState()
    val padding by preferences.webtoonSidePadding().collectAsState()
    val pageLayout by preferences.pageLayout().collectAsState()
    val splitWide by preferences.dualPageSplitPaged().collectAsState()
    val invertSplit by preferences.dualPageInvertPaged().collectAsState()
    val rotateWide by preferences.dualPageRotateToFit().collectAsState()
    val invertRotation by preferences.dualPageRotateToFitInvert().collectAsState()
    val grayscale by preferences.grayscale().collectAsState()
    val inverted by preferences.invertedColors().collectAsState()
    val customBrightness by preferences.customBrightness().collectAsState()
    val brightness by preferences.customBrightnessValue().collectAsState()
    val colorFilter by preferences.colorFilter().collectAsState()
    val colorValue by preferences.colorFilterValue().collectAsState()
    val colorMode by preferences.colorFilterMode().collectAsState()
    val dark = isSystemInDarkTheme()
    val background = when (theme) {
        0 -> Color.White
        2 -> Color(0xFF202125)
        3 -> if (dark) Color.Black else Color.White
        else -> Color.Black
    }
    val textColor = if (background == Color.White) Color.Black else Color.White
    val matrix = ReaderColorMatrix.of(grayscale = grayscale, inverted = inverted)
    val overlays = buildList {
        if (colorFilter) {
            val blend = ReaderPreferences.ColorFilterMode.getOrNull(colorMode)?.second ?: BlendMode.SrcOver
            add(Color(colorValue) to blend)
        }
        if (customBrightness && brightness < 0) add(Color.Black.copy(alpha = (-brightness / 100f).coerceIn(0f, 0.9f)) to BlendMode.SrcOver)
    }
    return ReaderDisplaySettings(
        viewer = ReaderDisplay(
            fit = PageFit.fromPreference(scale),
            background = background,
            textColor = textColor,
            colorFilter = matrix?.let(ColorFilter::colorMatrix),
            webtoonSidePaddingPercent = padding,
            pageLayout = pageLayout,
            splitWidePages = splitWide,
            invertSplit = invertSplit,
            rotateWidePages = rotateWide,
            invertRotation = invertRotation,
        ),
        overlays = overlays,
    )
}

/** Grayscale / inverted colour matrices (Android: `ReaderActivity` colour filter paint). */
object ReaderColorMatrix {
    fun of(grayscale: Boolean, inverted: Boolean): ColorMatrix? {
        if (!grayscale && !inverted) return null
        val result = ColorMatrix()
        if (grayscale) result.setToSaturation(0f)
        if (inverted) {
            result *= ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }
        return result
    }
}

/** Hosts the reader above [content] while a reader request is set; provides the fullscreen toggle. */
@Composable
fun ReaderHost(launcher: ReaderLauncher, onToggleFullscreen: (() -> Unit)?, content: @Composable () -> Unit) {
    val request by launcher.request.collectAsState()
    CompositionLocalProvider(LocalFullscreenToggle provides onToggleFullscreen) {
        Box(Modifier.fillMaxSize()) {
            content()
            request?.let { ReaderScreen(it, onClose = launcher::close) }
        }
    }
}
