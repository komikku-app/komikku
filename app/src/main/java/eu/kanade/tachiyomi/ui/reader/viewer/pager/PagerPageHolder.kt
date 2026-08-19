package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.UpscaleStatusIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.upscale.AiUpscaleCache
import eu.kanade.tachiyomi.util.upscale.UpscalePriorityGate
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import exh.log.xLogD
import exh.log.xLogE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.decoder.ImageDecoder
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/**
 * View of the ViewPager that contains a page of a chapter.
 */
@SuppressLint("ViewConstructor")
class PagerPageHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val page: ReaderPage,
    private var extraPage: ReaderPage? = null,
    // KMK -->
    @ColorInt private val seedColor: Int? = null,
    // KMK <--
) : ReaderPageImageView(readerThemedContext), ViewPagerAdapter.PositionableView {

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page to extraPage

    /**
     * Loading progress bar to indicate the current progress.
     */
    private var progressIndicator: ReaderProgressIndicator? = null // = ReaderProgressIndicator(readerThemedContext)

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    private val scope = MainScope()

    /**
     * Job for loading the page and processing changes to the page's status.
     */
    private var loadJob: Job? = null

    /**
     * Job for loading the page.
     */
    private var extraLoadJob: Job? = null

    // KMK -->
    private var upscaleIndicator: UpscaleStatusIndicator? = null
    private var crossfadeOverlay: ImageView? = null
    // KMK <--

    init {
        loadJob = scope.launch { loadPageAndProcessStatus(1) }
        // SY -->
        extraLoadJob = scope.launch { loadPageAndProcessStatus(2) }
        // SY <--
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadJob?.cancel()
        loadJob = null
        extraLoadJob?.cancel()
        extraLoadJob = null

        // KMK -->
        upscaleIndicator?.destroy()
        crossfadeOverlay?.animate()?.cancel()
        crossfadeOverlay = null
        // KMK <--
    }

    private fun initProgressIndicator() {
        if (progressIndicator == null) {
            progressIndicator = ReaderProgressIndicator(
                context = context,
                // KMK -->
                seedColor = seedColor,
                // KMK <--
            )
            addView(progressIndicator)
        }
    }

    // KMK -->
    private fun initUpscaleIndicator() {
        if (upscaleIndicator == null) {
            upscaleIndicator = UpscaleStatusIndicator(context, seedColor = seedColor, debugTag = "pagina${page.index}")
            addView(upscaleIndicator)
        }
    }
    // KMK <--

    // KMK -->
    private fun crossfadeToUpscaled(newSource: BufferedSource) {
        if (width <= 0 || height <= 0) {
            setImage(newSource, isAnimated = false, config = buildConfig())
            return
        }

        val locationInWindow = IntArray(2)
        getLocationInWindow(locationInWindow)
        val rect = Rect(
            locationInWindow[0],
            locationInWindow[1],
            locationInWindow[0] + width,
            locationInWindow[1] + height,
        )
        val snapshotBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        try {
            PixelCopy.request(
                viewer.activity.window,
                rect,
                snapshotBitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        // Rectangle cut to exclude the upscaling indicator to be in the Snapshot
                        val reservedWidthPx = 140.dpToPx
                        val reservedHeightPx = 56.dpToPx
                        val clearRect = Rect(
                            width - reservedWidthPx,
                            height - reservedHeightPx,
                            width,
                            height,
                        )
                        Canvas(snapshotBitmap).drawRect(
                            clearRect,
                            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) },
                        )

                        val overlay = ImageView(context).apply {
                            setImageBitmap(snapshotBitmap)
                            scaleType = ImageView.ScaleType.MATRIX
                            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        }
                        addView(overlay)
                        crossfadeOverlay = overlay
                        upscaleIndicator?.bringToFront()
                        requestLayout()
                        invalidate()
                    }
                    setImage(newSource, isAnimated = false, config = buildConfig())
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: Throwable) {
            setImage(newSource, isAnimated = false, config = buildConfig())
        }
    }
    // KMK <--

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus(pageIndex: Int) {
        // SY -->
        val page = if (pageIndex == 1) page else extraPage
        page ?: return
        // SY <--
        val loader = page.chapter.pageLoader ?: return
        supervisorScope {
            launchIO {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                // KMK -->
                xLogD("statusFlow issued for page ${page.index}: $state")
                // KMK <--
                when (state) {
                    Page.State.Queue -> setQueued()
                    Page.State.LoadPage -> setLoading()
                    Page.State.DownloadImage -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            progressIndicator?.setProgress(value)
                        }
                    }
                    Page.State.Ready -> setImage()
                    is Page.State.Error -> setError(state.error)
                }
            }
        }
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    // KMK -->
    private fun buildConfig() = Config(
        zoomDuration = viewer.config.doubleTapAnimDuration,
        minimumScaleType = viewer.config.imageScaleType,
        cropBorders = viewer.config.imageCropBorders,
        zoomStartPosition = viewer.config.imageZoomType,
        landscapeZoom = viewer.config.landscapeZoom,
        disableZoomIn = viewer.config.disableZoomIn,
        doubleTapZoom = viewer.config.doubleTapZoom,
        landscapeZoomScaleType = viewer.config.landscapeZoomScaleType,
    )
    // KMK <--

    // KMK -->
    private fun applyImage(source: BufferedSource, isAnimated: Boolean, background: Drawable?) {
        setImage(source, isAnimated, buildConfig())
        if (!isAnimated) pageBackground = background
        removeErrorLayout()
    }
    // KMK <--

    // KMK -->
    private suspend fun setImageWithUpscale(itemBytes: ByteArray, background: Drawable?, targetWidth: Int) {
        val upscaleDeferred = scope.async(Dispatchers.IO) {
            try {
                AiUpscaleCache.getOrUpscale(
                    chapterId = page.chapter.chapter.id,
                    pageIndex = page.index,
                    source = Buffer().write(itemBytes),
                    targetWidth = targetWidth,
                    priority = UpscalePriorityGate.Priority.VISIBLE,
                )
            } catch (e: Throwable) {
                xLogE("Upscaling failed for page ${page.index}", e)
                null
            }
        }

        val fastResult = withTimeoutOrNull(120.milliseconds) { upscaleDeferred.await() }

        if (fastResult != null) {
            withUIContext {
                applyImage(fastResult, isAnimated = false, background)
                initUpscaleIndicator()
                upscaleIndicator?.showActive()
            }
            return
        }

        withUIContext {
            applyImage(Buffer().write(itemBytes), isAnimated = false, background) // independent copy, UI only
            initUpscaleIndicator()
            upscaleIndicator?.showInProgress()
        }

        scope.launch {
            val lateResult = upscaleDeferred.await()
            withUIContext {
                if (lateResult != null) {
                    upscaleIndicator?.showSuccess()
                    postOnAnimation { crossfadeToUpscaled(lateResult) }
                } else {
                    upscaleIndicator?.showFailed()
                }
            }
        }
    }
    // KMK <--

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        // KMK -->
        xLogD("setImage() called for page ${page.index}")
        upscaleIndicator?.hide()
        // KMK <--
        if (extraPage == null) {
            progressIndicator?.setProgress(0)
        } else {
            progressIndicator?.setProgress(95)
        }

        val streamFn = page.stream ?: return
        val streamFn2 = extraPage?.stream

        try {
            val (itemBytes, isAnimated, background) = withIOContext {
                streamFn().buffered(16).use { source ->
                    // SY -->
                    if (extraPage != null) {
                        streamFn2?.invoke()
                            ?.buffered(16)
                    } else {
                        null
                    }.use { source2 ->
                        val itemSource = if (viewer.config.dualPageSplit) {
                            process(item.first, Buffer().readFrom(source))
                        } else {
                            mergePages(Buffer().readFrom(source), source2?.let { Buffer().readFrom(it) })
                        }
                        // SY <--
                        val isAnimated = ImageUtil.isAnimatedAndSupported(itemSource)
                        val background = if (!isAnimated && viewer.config.automaticBackground) {
                            ImageUtil.chooseBackground(context, itemSource.peek())
                        } else {
                            null
                        }
                        val bytes = itemSource.use { it.readByteArray() }
                        Triple(bytes, isAnimated, background)
                    }
                }
            }

            // KMK -->
            val upscalePrefs = Injekt.get<ReaderPreferences>()
            val targetWidth = context.resources.displayMetrics.widthPixels
            val upscaleEnabled = upscalePrefs.aiUpscaleEnabled().get() && !isAnimated

            if (!upscaleEnabled) {
                withUIContext { applyImage(Buffer().write(itemBytes), isAnimated, background) }
            } else {
                setImageWithUpscale(itemBytes, background, targetWidth)
            }
            // KMK <--
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                setError(e)
            }
        }
    }

    private fun process(page: ReaderPage, imageSource: BufferedSource): BufferedSource {
        if (viewer.config.dualPageRotateToFit) {
            return rotateDualPage(imageSource)
        }

        if (!viewer.config.dualPageSplit) {
            return imageSource
        }

        if (page is InsertPage) {
            return splitInHalf(imageSource)
        }

        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (!isDoublePage) {
            return imageSource
        }

        onPageSplit(page)

        return splitInHalf(imageSource)
    }

    private fun rotateDualPage(imageSource: BufferedSource): BufferedSource {
        val isDoublePage = ImageUtil.isWideImage(imageSource)
        return if (isDoublePage) {
            val rotation = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
            ImageUtil.rotateImage(imageSource, rotation)
        } else {
            imageSource
        }
    }

    private fun mergePages(imageSource: BufferedSource, imageSource2: BufferedSource?): BufferedSource {
        // Handle adding a center margin to wide images if requested
        if (imageSource2 == null) {
            return handleWideImage(imageSource)
        }

        if (page.fullPage) return imageSource
        if (ImageUtil.isAnimatedAndSupported(imageSource)) {
            page.fullPage = true
            splitDoublePages()
            return imageSource
        } else if (ImageUtil.isAnimatedAndSupported(imageSource2)) {
            page.isolatedPage = true
            extraPage?.fullPage = true
            splitDoublePages()
            return imageSource
        }

        val imageBitmap = decodeImage(imageSource)
        if (imageBitmap == null) {
            imageSource2.close()
            page.fullPage = true
            splitDoublePages()
            logcat(LogPriority.ERROR) { "Cannot combine pages" }
            return imageSource
        }

        scope.launch { progressIndicator?.setProgress(96) }
        if (imageBitmap.height < imageBitmap.width) {
            imageSource2.close()
            page.fullPage = true
            splitDoublePages()
            return imageSource
        }

        val imageBitmap2 = decodeImage(imageSource2)
        if (imageBitmap2 == null) {
            imageSource2.close()
            extraPage?.fullPage = true
            page.isolatedPage = true
            splitDoublePages()
            logcat(LogPriority.ERROR) { "Cannot combine pages" }
            return imageSource
        }

        scope.launch { progressIndicator?.setProgress(97) }
        if (imageBitmap2.height < imageBitmap2.width) {
            imageSource2.close()
            extraPage?.fullPage = true
            page.isolatedPage = true
            splitDoublePages()
            return imageSource
        }

        val isLTR = (viewer !is R2LPagerViewer) xor viewer.config.invertDoublePages
        val centerMargin = calculateCenterMargin(imageBitmap.height, imageBitmap2.height)

        imageSource.close()
        imageSource2.close()

        return ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, centerMargin, viewer.config.pageCanvasColor) {
            updateProgress(it)
        }
    }

    private fun handleWideImage(imageSource: BufferedSource): BufferedSource {
        return if (
            !ImageUtil.isAnimatedAndSupported(imageSource) &&
            ImageUtil.isWideImage(imageSource) &&
            viewer.config.centerMarginType and PagerConfig.CenterMarginType.WIDE_PAGE_CENTER_MARGIN > 0 &&
            !viewer.config.imageCropBorders
        ) {
            ImageUtil.addHorizontalCenterMargin(imageSource, height, context)
        } else {
            imageSource
        }
    }

    private fun decodeImage(imageSource: BufferedSource): Bitmap? {
        return try {
            ImageDecoder.newInstance(imageSource.inputStream())?.decode()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Cannot decode image" }
            null
        }
    }

    private fun calculateCenterMargin(height: Int, height2: Int): Int {
        return if (viewer.config.centerMarginType and PagerConfig.CenterMarginType.DOUBLE_PAGE_CENTER_MARGIN > 0 &&
            !viewer.config.imageCropBorders
        ) {
            96 / (this.height.coerceAtLeast(1) / max(height, height2).coerceAtLeast(1)).coerceAtLeast(1)
        } else {
            0
        }
    }

    private fun updateProgress(progress: Int) {
        scope.launch {
            if (progress == 100) {
                progressIndicator?.hide()
            } else {
                progressIndicator?.setProgress(progress)
            }
        }
    }

    private fun splitDoublePages() {
        scope.launch {
            delay(100)
            viewer.splitDoublePages(page)
            if (extraPage?.fullPage == true || page.fullPage) {
                extraPage = null
            }
        }
    }

    private fun splitInHalf(imageSource: BufferedSource): BufferedSource {
        var side = when {
            viewer is L2RPagerViewer && page is InsertPage -> ImageUtil.Side.RIGHT
            viewer !is L2RPagerViewer && page is InsertPage -> ImageUtil.Side.LEFT
            viewer is L2RPagerViewer && page !is InsertPage -> ImageUtil.Side.LEFT
            viewer !is L2RPagerViewer && page !is InsertPage -> ImageUtil.Side.RIGHT
            else -> error("We should choose a side!")
        }

        if (viewer.config.dualPageInvert) {
            side = when (side) {
                ImageUtil.Side.RIGHT -> ImageUtil.Side.LEFT
                ImageUtil.Side.LEFT -> ImageUtil.Side.RIGHT
            }
        }

        val sideMargin = if ((viewer.config.centerMarginType and PagerConfig.CenterMarginType.DOUBLE_PAGE_CENTER_MARGIN) >
            0 &&
            viewer.config.doublePages &&
            !viewer.config.imageCropBorders
        ) {
            48
        } else {
            0
        }

        return ImageUtil.splitInHalf(imageSource, side, sideMargin)
    }

    private fun onPageSplit(page: ReaderPage) {
        val newPage = InsertPage(page)
        viewer.onPageSplit(page, newPage)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError(error: Throwable?) {
        progressIndicator?.hide()
        showErrorLayout(error)
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        progressIndicator?.hide()
        removeErrorLayout()

        // KMK -->
        crossfadeOverlay?.let { overlay ->
            xLogD("[page${page.index}] crossfade: beginning fade overlay")
            overlay.animate()
                .alpha(0f)
                .setDuration(250L)
                .withEndAction {
                    xLogD("[page${page.index}] crossfade: overlay removed")
                    removeView(overlay)
                    overlay.setImageBitmap(null)
                }
                .start()
            crossfadeOverlay = null
        }
        // KMK <--
    }
    /**
     * Called when an image fails to decode.
     */
    override fun onImageLoadError(error: Throwable?) {
        super.onImageLoadError(error)
        setError(error)
    }

    /**
     * Called when an image is zoomed in/out.
     */
    override fun onScaleChanged(newScale: Float) {
        super.onScaleChanged(newScale)
        viewer.activity.hideMenu()
    }

    private fun showErrorLayout(error: Throwable?): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), this, true)
            errorLayout?.actionRetry?.viewer = viewer
            errorLayout?.actionRetry?.setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }
        }

        val imageUrl = page.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null) {
            if (imageUrl.startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.viewer = viewer
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val sourceId = viewer.activity.viewModel.manga?.source

                    val intent = WebViewActivity.newIntent(context, imageUrl, sourceId)
                    context.startActivity(intent)
                }
            }
        }

        errorLayout?.errorMessage?.text = with(context) { error?.formattedMessage }
            ?: context.stringResource(MR.strings.decode_image_error)

        errorLayout?.root?.isVisible = true
        return errorLayout!!
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeErrorLayout() {
        errorLayout?.root?.isVisible = false
        errorLayout = null
    }
}
