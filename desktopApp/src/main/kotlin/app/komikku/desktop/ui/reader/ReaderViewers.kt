package app.komikku.desktop.ui.reader

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.shared.reader.image.PageFit
import app.komikku.shared.reader.image.PageScaling
import app.komikku.shared.reader.image.PixelSize
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderItem
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/** Zoom and pan of the reader content (scale 1 = the fit mode's size). */
@Stable
class ReaderZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun zoomBy(factor: Float) {
        scale = (scale * factor).coerceIn(MIN, MAX)
        if (scale == 1f) offset = Offset.Zero
    }

    fun toggle(target: Float = 2f) = if (scale > 1f) reset() else zoomBy(target / scale)

    /** Pans by [delta], keeping the scaled [content] covering the [viewport] where it is larger. */
    fun pan(delta: Offset, content: Pair<Float, Float>, viewport: Pair<Float, Float>): Offset {
        val maxX = ((content.first * scale - viewport.first) / 2).coerceAtLeast(0f)
        val maxY = ((content.second * scale - viewport.second) / 2).coerceAtLeast(0f)
        val target = offset + delta
        val clamped = Offset(target.x.coerceIn(-maxX, maxX), target.y.coerceIn(-maxY, maxY))
        val consumed = clamped - offset
        offset = clamped
        return consumed
    }

    companion object {
        const val MIN = 0.5f
        const val MAX = 6f
        const val STEP = 1.25f
    }
}

/** Display options for both viewers, resolved from `ReaderPreferences`. */
data class ReaderDisplay(
    val fit: PageFit,
    val background: Color,
    val textColor: Color,
    val colorFilter: ColorFilter?,
    val webtoonSidePaddingPercent: Int,
    val doublePages: Boolean,
)

/**
 * Paged viewer (LTR, RTL, vertical): one item at a time — a page, or with [ReaderDisplay.doublePages]
 * two adjacent pages side by side (in reading direction) — fitted per [ReaderDisplay.fit], zoom
 * (Ctrl + wheel, +/-/0, double-click) and pan (drag, wheel when the page is taller than the window).
 * Click zones turn pages; the neighbours are loaded and decoded ahead.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PagedViewer(
    state: ReaderModel.State,
    zoom: ReaderZoom,
    display: ReaderDisplay,
    onAction: (ReaderAction) -> Unit,
    onRetry: (ReaderPage) -> Unit,
) {
    val item = state.currentItem
    LaunchedEffect(state.currentIndex, state.viewerChapters) { zoom.reset() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("paged-viewer")
            .pointerInput(state.readingMode) {
                detectTapGestures(
                    onDoubleTap = { zoom.toggle() },
                    onTap = { position ->
                        onAction(ReaderKeyMap.clickAction(position.x / size.width, position.y / size.height, state.readingMode))
                    },
                )
            },
    ) {
        val density = LocalDensity.current
        val viewport = PixelSize(constraints.maxWidth, constraints.maxHeight)
        // Content size of the shown item in px at zoom 1, for pan limits and wheel handling.
        var contentSize by remember { mutableStateOf(0f to 0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        zoom.pan(dragAmount, contentSize, viewport.width.toFloat() to viewport.height.toFloat())
                    }
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val change = event.changes.firstOrNull() ?: return@onPointerEvent
                    val dy = change.scrollDelta.y
                    if (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed) {
                        zoom.zoomBy(if (dy < 0) ZOOM_WHEEL_STEP else 1 / ZOOM_WHEEL_STEP)
                    } else {
                        val consumed = zoom.pan(
                            Offset(0f, -dy * WHEEL_PAN_PX),
                            contentSize,
                            viewport.width.toFloat() to viewport.height.toFloat(),
                        )
                        if (abs(consumed.y) < 0.5f && dy != 0f) onAction(if (dy > 0) ReaderAction.Next else ReaderAction.Previous)
                    }
                    change.consume()
                },
            contentAlignment = Alignment.Center,
        ) {
            when (item) {
                is ReaderPage -> {
                    val spread = if (display.doublePages && state.readingMode.direction == ReadingMode.Direction.Horizontal) {
                        // Pages of one chapter only (the next item may be the next chapter's first page)
                        (state.items.getOrNull(state.currentIndex + 1) as? ReaderPage)?.takeIf { it.chapter == item.chapter }
                    } else {
                        null
                    }
                    val pages = listOfNotNull(item, spread).let { if (state.readingMode == ReadingMode.RIGHT_TO_LEFT) it.reversed() else it }
                    val slotWidth = viewport.width / pages.size
                    Row(
                        modifier = Modifier.graphicsLayer {
                            scaleX = zoom.scale
                            scaleY = zoom.scale
                            translationX = zoom.offset.x
                            translationY = zoom.offset.y
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        pages.forEach { page ->
                            Box(Modifier.testTag("page-${page.index}")) {
                                ReaderPageSlot(page, maxWidth = (slotWidth * 2).coerceAtLeast(1), display.colorFilter, onRetry = { onRetry(page) }) { decoded ->
                                    val (w, h) = PageScaling.displaySize(decoded.decoded, PixelSize(slotWidth, viewport.height), display.fit)
                                    contentSize = (w * pages.size) to h
                                    with(density) {
                                        PageTiles(decoded, display.colorFilter, Modifier.requiredSize(w.toDp(), h.toDp()))
                                    }
                                }
                            }
                        }
                    }
                }
                is ChapterTransition -> {
                    contentSize = 0f to 0f
                    ChapterTransitionContent(item, display.textColor, Modifier.fillMaxSize())
                }
                null -> {}
            }
        }
        // Preload (load + decode) the neighbouring pages.
        listOf(state.currentIndex - 1, state.currentIndex + 1, state.currentIndex + 2)
            .mapNotNull { state.items.getOrNull(it) as? ReaderPage }
            .forEach { page -> key(page) { rememberPageImage(page, viewport.width * 2) } }
    }
}

/**
 * Webtoon / continuous vertical viewer: a lazy column of all items of the current chapter with the
 * side padding setting, native smooth wheel scrolling, keyboard scrolling from the reader, tall pages
 * as stacked strips. Item heights stay stable once a page's aspect ratio is known (also after the
 * decoded image left the memory cache). The list holds the neighbouring chapters' pages once they are loaded (P11):
 * the item on top is reported to the model ([onMoveTo]), which preloads and switches chapters like Android.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WebtoonViewer(
    state: ReaderModel.State,
    listState: LazyListState,
    zoom: ReaderZoom,
    display: ReaderDisplay,
    onAction: (ReaderAction) -> Unit,
    onMoveTo: (ReaderItem) -> Unit,
    onRetry: (ReaderPage) -> Unit,
) {
    // Page aspect ratios by item key: the list holds pages of up to three chapters (P11)
    val aspects = remember { mutableStateMapOf<Any, Float>() }
    // A position set by the reader (chapter opened, page / chapter jump): scroll there. Other item changes (a
    // neighbour's pages added, the current chapter switched) keep the list where it is (lazy list keys).
    LaunchedEffect(state.jump) {
        if (state.items.isNotEmpty()) listState.scrollToItem(state.currentIndex)
    }
    val items by rememberUpdatedState(state.items)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> items.getOrNull(index)?.let(onMoveTo) }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("webtoon-viewer")
            .onPointerEvent(PointerEventType.Scroll) { event ->
                if (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed) {
                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    zoom.zoomBy(if (dy < 0) ZOOM_WHEEL_STEP else 1 / ZOOM_WHEEL_STEP)
                    event.changes.forEach { it.consume() }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { zoom.toggle() },
                    onTap = { onAction(ReaderAction.ToggleMenu) },
                )
            },
    ) {
        val padding = maxWidth * (display.webtoonSidePaddingPercent / 100f)
        val contentWidthPx = with(LocalDensity.current) { (maxWidth - padding * 2).roundToPx() }.coerceAtLeast(1)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = padding),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                },
        ) {
            itemsIndexed(state.items, key = { i, item -> itemKey(i, item) }) { i, item ->
                when (item) {
                    is ReaderPage -> {
                        val key = itemKey(i, item)
                        ReaderPageSlotWebtoon(item, contentWidthPx, display, aspects[key], onAspect = { aspects[key] = it }, onRetry = { onRetry(item) })
                    }
                    is ChapterTransition -> ChapterTransitionContent(
                        item,
                        display.textColor,
                        Modifier.fillMaxWidth().height(maxHeight * 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderPageSlotWebtoon(
    page: ReaderPage,
    widthPx: Int,
    display: ReaderDisplay,
    knownAspect: Float?,
    onAspect: (Float) -> Unit,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().testTag("page-${page.index}")) {
        when (val image = rememberPageImage(page, widthPx)) {
            is PageImageState.Ready -> {
                val aspect = image.page.decoded.aspect
                if (knownAspect != aspect) LaunchedEffect(aspect) { onAspect(aspect) }
                PageTiles(image.page, display.colorFilter, Modifier.fillMaxWidth())
            }
            else -> {
                val height = with(LocalDensity.current) {
                    knownAspect?.let { (widthPx / it).toDp() } ?: 600.dp
                }
                PagePlaceholder(page, image, onRetry, Modifier.fillMaxWidth().height(height).wrapContentSize())
            }
        }
    }
}

private fun itemKey(index: Int, item: ReaderItem): Any = when (item) {
    is ReaderPage -> "page-${item.chapter.chapter.id}-${item.index}"
    // One transition of each kind per list, named by its chapters so that it keeps its key when the list is rebuilt
    is ChapterTransition.Prev -> "prev-${item.from.chapter.id}-${item.to?.chapter?.id}"
    is ChapterTransition.Next -> "next-${item.from.chapter.id}-${item.to?.chapter?.id}"
}

/** Scrolls the webtoon list for keyboard actions; returns `false` for actions it does not handle. */
suspend fun LazyListState.scrollFor(action: ReaderAction, viewportHeight: Float): Boolean {
    val delta = when (action) {
        ReaderAction.ScrollDown -> viewportHeight * 0.15f
        ReaderAction.ScrollUp -> -viewportHeight * 0.15f
        ReaderAction.PageDown -> viewportHeight * 0.9f
        ReaderAction.PageUp -> -viewportHeight * 0.9f
        else -> return false
    }
    animateScrollBy(delta)
    return true
}

private const val ZOOM_WHEEL_STEP = 1.1f
private const val WHEEL_PAN_PX = 60f
