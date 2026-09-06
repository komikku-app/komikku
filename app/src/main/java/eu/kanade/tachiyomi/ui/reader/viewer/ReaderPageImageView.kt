package eu.kanade.tachiyomi.ui.reader.viewer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.animation.doOnEnd
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import coil3.BitmapImage
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.ViewSizeResolver
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.coil.cropBorders
import eu.kanade.tachiyomi.data.coil.customDecoder
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.LandscapeZoomScaleType
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import eu.kanade.tachiyomi.util.waifu2x.ImageEnhancementCache
import eu.kanade.tachiyomi.util.waifu2x.ImageEnhancer
import eu.kanade.tachiyomi.util.waifu2x.Waifu2x
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.math.roundToInt

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonSubsamplingImageView] will be used instead of [SubsamplingScaleImageView]
 * and [AppCompatImageView] will be used instead of [PhotoView]
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttrs: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {

    private val alwaysDecodeLongStripWithSSIV by lazy {
        Injekt.get<BasePreferences>().alwaysDecodeLongStripWithSSIV().get()
    }

    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var isSettingProcessedImage = false // Flag to prevent recursive processing

    // KMK -->
    private val preferences: ReaderPreferences by injectLazy()

    private val realCuganEnabled: Boolean
        get() = preferences.realCuganEnabled().get()

    private val realCuganNoiseLevel: Int
        get() = preferences.realCuganNoiseLevel().get()

    private val realCuganScale: Int
        get() = preferences.realCuganScale().get()

    private val preloadSize: Int
        get() = if (preferences.realCuganEnabled().get()) preferences.realCuganPreloadSize().get() else 4

    private val realCuganModel: Int
        get() = preferences.realCuganModel().get()

    private val realCuganMaxSizeWidth: Int
        get() = preferences.realCuganMaxSizeWidth().get()

    private val realCuganMaxSizeHeight: Int
        get() = preferences.realCuganMaxSizeHeight().get()

    private val realCuganSkipMaxSizeWidth: Int
        get() = preferences.realCuganSkipMaxSizeWidth().get()

    private val realCuganSkipMaxSizeHeight: Int
        get() = preferences.realCuganSkipMaxSizeHeight().get()

    private val realCuganShowStatus: Boolean
        get() = preferences.realCuganShowStatus().get()

    // Performance mode: 0=full speed, 1=balanced, 2=power saving.
    private val realCuganPerformanceMode: Int
        get() = preferences.realCuganPerformanceMode().get()

    private val tileSleepMs: Int
        get() = when (realCuganPerformanceMode) {
            0 -> 0
            1 -> 5
            2 -> 15
            else -> 0
        }

    private val tileSize: Int
        get() = preferences.realCuganTileSize().get().coerceAtLeast(32)
    // KMK <--

    private var pageView: View? = null
    private var currentLoadedUri: String? = null
    private var lastStatusText: String? = null

    // KMK -->
    var enhancedImageSourceFactory: ((java.io.File) -> BufferedSource?)? = null
    var suppressDefaultStatus = false
    /** Whether the enhanced image can be swapped in for the current display (false for merged double pages). */
    var enhancementDisplayEnabled: Boolean = true
    // KMK <--

    private var config: Config? = null

    // KMK -->
    private var processingJob: Job? = null
    private var enhancedBitmap: Bitmap? = null
    private var processedSwapView: SubsamplingScaleImageView? = null
    private var outgoingProcessedView: SubsamplingScaleImageView? = null
    private var processedSwapAnimator: ValueAnimator? = null

    // Helper properties for Enhancement logic
    var pageIndex: Int = -1
    var mangaId: Long = -1L
    var chapterId: Long = -1L
    var readerPage: ReaderPage? = null
    var enhancementVariantOverride: String? = null
    var enhancementStreamOverride: (() -> java.io.InputStream)? = null
    var processedTransitionStartFraction: Float = 0f
    var processedTransitionEndFraction: Float = 1f
    var controlsCurrentPageSelection: Boolean = true
    // KMK <--

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onScaleChanged: ((newScale: Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null

    /**
     * For automatic background. Will be set as background color when [onImageLoaded] is called.
     */
    var pageBackground: Drawable? = null

    init {
        // KMK -->
        // Listen for performance mode changes to update native throttling immediately
        // Use launchIO to avoid blocking UI thread waiting for native lock if processing is active
        viewScope.launchIO {
            // Check cache size and trim if needed (debounced to run at most once every 10 mins)
            ImageEnhancementCache.checkAndTrim(context)

            preferences.realCuganPerformanceMode().changes()
                .collect { mode ->
                    val sleepMs = when (mode) {
                        0 -> 0
                        1 -> 5
                        2 -> 15
                        else -> 0
                    }
                    Waifu2x.updatePerformance(sleepMs, tileSize)
                }
        }

        viewScope.launchIO {
            preferences.realCuganTileSize().changes()
                .collect { size ->
                    Waifu2x.updatePerformance(tileSleepMs, size.coerceAtLeast(32))
                }
        }

        viewScope.launchIO {
            preferences.realCuganShowStatus().changes()
                .collect { enabled ->
                    withUIContext {
                        if (!enabled) {
                            statusView.isVisible = false
                        } else if (lastStatusText != null) {
                            statusView.text = lastStatusText
                            statusView.isVisible = true
                            statusView.bringToFront()
                        }
                    }
                }
        }
        // KMK <--
    }

    // KMK -->
    private val statusView: TextView by lazy {
        TextView(context).apply {
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(20, 0, 0, 20)
            }
            setTextColor(Color.WHITE)
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            isVisible = false
            this@ReaderPageImageView.addView(this)
        }
    }

    private val enhancedOverlay: AppCompatImageView by lazy {
        AppCompatImageView(context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isVisible = false
            this@ReaderPageImageView.addView(this)
        }
    }

    private fun updateStatus(text: String?) {
        lastStatusText = text
        post {
            if (suppressDefaultStatus) {
                statusView.isVisible = false
                return@post
            }
            if (!realCuganShowStatus || text == null) {
                statusView.isVisible = false
                return@post
            }
            statusView.text = text
            statusView.isVisible = true
            statusView.bringToFront()
        }
    }

    private fun decodeEnhancedBitmap(file: java.io.File): Bitmap? {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
            if (ImageEnhancementCache.isDisplayable(bitmap)) {
                bitmap
            } else {
                logcat(LogPriority.WARN) { "Ignoring invalid enhanced cache: ${file.absolutePath}" }
                bitmap.recycle()
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    // KMK <--

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground

        // KMK -->
        // Keep the processed preview fully covering the page until the replacement image is ready,
        // then switch instantly to avoid exposing SSIV's blank loading state.
        if (isSettingProcessedImage) {
            pageView?.alpha = 1f
            enhancedOverlay.animate().cancel()
            enhancedOverlay.alpha = 0f
            enhancedOverlay.isVisible = false
            enhancedOverlay.setImageBitmap(null)
            enhancedBitmap?.recycle()
            enhancedBitmap = null
            isSettingProcessedImage = false
        }
        // KMK <--
    }

    @CallSuper
    open fun onImageLoadError(error: Throwable?) {
        onImageLoadError?.invoke(error)

        // KMK -->
        // Hide overlay and recycle temporary bitmap if enhanced image load failed
        if (isSettingProcessedImage) {
            pageView?.alpha = 1f
            enhancedOverlay.setImageBitmap(null)
            enhancedOverlay.isVisible = false
            enhancedBitmap?.recycle()
            enhancedBitmap = null
            isSettingProcessedImage = false
        }
        // KMK <--
    }

    @CallSuper
    open fun onScaleChanged(newScale: Float) {
        onScaleChanged?.invoke(newScale)

        // KMK -->
        if (processedSwapAnimator?.isRunning == true) {
            completeProcessedSwapTransition(notifyLoaded = true)
        }

        // If zooming, dismiss the static overlay immediately to show the zoomable image
        if (newScale != 1f && isSettingProcessedImage) {
            enhancedOverlay.animate().cancel()
            enhancedOverlay.isVisible = false
            enhancedOverlay.setImageBitmap(null)
            enhancedBitmap?.recycle()
            enhancedBitmap = null
            isSettingProcessedImage = false
            pageView?.alpha = 1f
        }
        // KMK <--
    }

    // KMK -->
    private fun clearProcessedSwapView() {
        processedSwapAnimator?.run {
            removeAllUpdateListeners()
            removeAllListeners()
            cancel()
        }
        processedSwapAnimator = null

        val swapView = processedSwapView
        val outgoingView = outgoingProcessedView
        if (swapView != null) {
            if (pageView === swapView) {
                pageView = outgoingView
            }
            // KMK: detach before recycling - recycling an attached SSIV nulls vTranslate and a
            // subsequent draw pass crashes in sendStateChanged.
            swapView.setOnStateChangedListener(null)
            removeView(swapView)
            swapView.recycle()
        }
        outgoingView?.alpha = 1f
        outgoingView?.clipBounds = null
        processedSwapView = null
        outgoingProcessedView = null
    }

    private fun completeProcessedSwapTransition(notifyLoaded: Boolean) {
        processedSwapAnimator?.run {
            removeAllUpdateListeners()
            removeAllListeners()
            cancel()
        }
        processedSwapAnimator = null

        val swapView = processedSwapView ?: return
        swapView.alpha = 1f
        swapView.clipBounds = null
        pageView = swapView

        outgoingProcessedView?.let { outgoingView ->
            if (outgoingView !== swapView) {
                // KMK: detach before recycling (see clearProcessedSwapView).
                outgoingView.setOnStateChangedListener(null)
                removeView(outgoingView)
                outgoingView.recycle()
            }
        }
        processedSwapView = null
        outgoingProcessedView = null

        if (notifyLoaded) {
            onImageLoaded()
        }
    }

    private fun displayedImageRect(view: SubsamplingScaleImageView): RectF? {
        val center = view.center ?: return null
        val scale = view.scale
        val sourceWidth = view.sWidth
        val sourceHeight = view.sHeight
        if (scale <= 0f || sourceWidth <= 0 || sourceHeight <= 0 || view.width <= 0 || view.height <= 0) {
            return null
        }

        val left = view.width / 2f - center.x * scale
        val top = view.height / 2f - center.y * scale
        return RectF(
            left,
            top,
            left + sourceWidth * scale,
            top + sourceHeight * scale,
        )
    }

    private fun animateProcessedSwap(
        activeView: SubsamplingScaleImageView,
        targetConfig: Config?,
        setImageBlock: SubsamplingScaleImageView.() -> Unit,
    ) {
        clearProcessedSwapView()

        val targetScale = activeView.scale
        val targetCenter = activeView.center?.let { PointF(it.x, it.y) }
        val targetMinScale = activeView.minScale
        val targetWidth = activeView.sWidth
        val targetHeight = activeView.sHeight
        outgoingProcessedView = activeView
        val swapView = createSubsamplingPageView().apply {
            alpha = 0f
            isVisible = true
            setDoubleTapZoomDuration((targetConfig?.zoomDuration ?: 500).getSystemScaledDuration())
            setMinimumScaleType(targetConfig?.minimumScaleType ?: SCALE_TYPE_CENTER_INSIDE)
            setMinimumDpi(1)
            setCropBorders(targetConfig?.cropBorders ?: false)
            setOnImageEventListener(
                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                    override fun onReady() {
                        if (processedSwapView !== this@apply) return

                        setupZoom(targetConfig)

                        val wasZoomed =
                            targetScale > 0f &&
                                targetCenter != null &&
                                targetMinScale > 0f &&
                                targetWidth > 0 &&
                                targetHeight > 0 &&
                                targetScale > targetMinScale + 0.01f

                        if (wasZoomed) {
                            val zoomFactor = targetScale / targetMinScale
                            val mappedCenter = PointF(
                                (targetCenter.x / targetWidth) * sWidth,
                                (targetCenter.y / targetHeight) * sHeight,
                            )
                            val mappedScale = (minScale * zoomFactor).coerceIn(minScale, maxScale)
                            setScaleAndCenter(mappedScale, mappedCenter)
                        } else if (isVisibleOnScreen()) {
                            landscapeZoom(true)
                        }

                        bringToFront()
                        statusView.bringToFront()
                        pageView = this@apply
                        val revealStart = processedTransitionStartFraction.coerceIn(0f, 1f)
                        val revealEnd = processedTransitionEndFraction.coerceIn(revealStart, 1f)
                        val imageRect = displayedImageRect(this@apply)
                        val contentLeft = imageRect?.left ?: 0f
                        val contentWidth = imageRect?.width() ?: width.toFloat()
                        val viewWidth = width.coerceAtLeast(1)
                        val clipLeft = (contentLeft + contentWidth * revealStart)
                            .roundToInt()
                            .coerceIn(0, viewWidth - 1)
                        val clipRight = (contentLeft + contentWidth * revealEnd)
                            .roundToInt()
                            .coerceIn(clipLeft + 1, viewWidth)
                        clipBounds = Rect(clipLeft, 0, clipRight, height)
                        alpha = 0f

                        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = PROCESSED_SWAP_DURATION_MS
                            interpolator = FastOutSlowInInterpolator()
                            addUpdateListener { animator ->
                                alpha = animator.animatedValue as Float
                            }
                            doOnEnd {
                                if (processedSwapAnimator === this) {
                                    processedSwapAnimator = null
                                    completeProcessedSwapTransition(notifyLoaded = true)
                                }
                            }
                        }
                        processedSwapAnimator = animator
                        animator.start()
                    }

                    override fun onImageLoadError(e: Exception) {
                        clearProcessedSwapView()
                        this@ReaderPageImageView.onImageLoadError(e)
                    }
                },
            )
        }
        processedSwapView = swapView
        addView(swapView, 0, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        swapView.setImageBlock()
    }

    // KMK <--

    @CallSuper
    open fun onViewClicked() {
        onViewClicked?.invoke()
    }

    open fun onPageSelected(forward: Boolean) {
        // KMK --> Enhancement cache status / pruning
        val mId = readerPage?.chapter?.chapter?.manga_id ?: mangaId
        val cId = readerPage?.chapter?.chapter?.id ?: chapterId
        val pIdx = readerPage?.index ?: pageIndex

        // Update global current page index to help other instances decide if they should self-heal
        if (controlsCurrentPageSelection && pIdx >= 0) {
            currentGlobalPageIndex = pIdx
            ImageEnhancer.reprioritizeAround(pIdx, enhancementVariant())
        }

        if (pIdx >= 0 && mId != -1L && cId != -1L && realCuganEnabled && enhancementDisplayEnabled) {
            ImageEnhancementCache.init(context)
            val configHash = ImageEnhancementCache.getConfigHash(
                noise = realCuganNoiseLevel,
                scale = realCuganScale,
                model = realCuganModel,
                realEsrganStyle = preferences.realEsrganStyle().get(),
                maxWidth = realCuganMaxSizeWidth,
                maxHeight = realCuganMaxSizeHeight,
                skipMaxWidth = realCuganSkipMaxSizeWidth,
                skipMaxHeight = realCuganSkipMaxSizeHeight,
                tileSize = tileSize,
                precision = preferences.realCuganPrecision().get(),
                fp16Arithmetic = preferences.realCuganFp16Arithmetic().get(),
                processingBackend = preferences.realCuganProcessingBackend().get(),
            )
            val pageVariant = enhancementVariant()

            val cachedFile = ImageEnhancementCache.getCachedImage(mId, cId, pIdx, configHash, pageVariant)
            if (cachedFile != null) {
                // Already processed - update status and load enhanced image
                val uriString = cachedFile.toURI().toString()
                if (currentLoadedUri != uriString) {
                    // Load the enhanced image if not already loaded or if source changed
                    viewScope.launchIO {
                        val transformedSource = enhancedImageSourceFactory?.invoke(cachedFile)
                        if (transformedSource != null) {
                            withUIContext {
                                setProcessedSource(cachedFile, transformedSource = transformedSource)
                            }
                            return@launchIO
                        }

                        val bitmap = decodeEnhancedBitmap(cachedFile)

                        if (bitmap != null) {
                            withUIContext {
                                setProcessedSource(cachedFile, bitmap = bitmap)
                            }
                        } else {
                            healInvalidEnhancedCache(mId, cId, pIdx, configHash, readerPage, forceCurrentPage = true)
                            startEnhancementPolling(mId, cId, pIdx, configHash, readerPage)
                        }
                    }
                } else {
                    updateStatus(context.stringResource(KMR.strings.reader_status_processed))
                }
            } else if (ImageEnhancementCache.isSkipped(mId, cId, pIdx, configHash, pageVariant)) {
                updateStatus(context.stringResource(KMR.strings.reader_status_raw))
            } else {
                // Not in cache, not skipped - ensure it's being processed with high priority
                updateStatus(context.stringResource(KMR.strings.reader_status_processing))
                enqueueEnhancement(mId, cId, pIdx, highPriority = true)

                // Start/restart polling if not already running
                startEnhancementPolling(mId, cId, pIdx, configHash)
            }
        }

        // Prune others
        if (pIdx >= 0 && mId != -1L && cId != -1L) {
            ImageEnhancer.cancelRequestsLessThan(context.applicationContext, mId, cId, pIdx)
            ImageEnhancer.cancelRequestsGreaterThan(context.applicationContext, mId, cId, pIdx + preloadSize)
        }
        // KMK <--

        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageLoadError(e)
                        }
                    },
                )
            }
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean) {
        val config = config
        if (config != null &&
            config.landscapeZoom &&
            config.minimumScaleType == SCALE_TYPE_CENTER_INSIDE &&
            sWidth > sHeight &&
            scale == minScale
        ) {
            handler?.postDelayed(500) {
                val point = when (config.zoomStartPosition) {
                    ZoomStartPosition.LEFT -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                    ZoomStartPosition.RIGHT -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                    ZoomStartPosition.CENTER -> center
                }

                val targetScale = /* KMK --> */ when (config.landscapeZoomScaleType) {
                    LandscapeZoomScaleType.DOUBLE -> scale * 2
                    // KMK <--
                    else -> height.toFloat() / sHeight.toFloat()
                }
                (animateScaleAndCenter(targetScale, point) ?: return@postDelayed)
                    .withDuration(500)
                    .withEasing(EASE_IN_OUT_QUAD)
                    .withInterruptible(true)
                    .start()
            }
        }
    }

    fun setImage(drawable: Drawable, config: Config) {
        this.config = config
        if (drawable is Animatable) {
            prepareAnimatedImageView()
            setAnimatedImage(drawable, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(drawable, config)
        }
    }

    fun setImage(source: BufferedSource, isAnimated: Boolean, config: Config, streamFn: (() -> java.io.InputStream)? = null) {
        this.config = config
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(source, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config, streamFn)
        }
    }

    // KMK -->
    fun recycle() {
        clearProcessedSwapView()
        pageView?.let {
            processingJob?.cancel()
            processingJob = null

            when (it) {
                is SubsamplingScaleImageView -> it.recycle()
                is AppCompatImageView -> it.dispose()
            }
            it.isVisible = false
            enhancedOverlay.setImageBitmap(null)
            enhancedOverlay.isVisible = false
            enhancedBitmap?.recycle()
            enhancedBitmap = null
            isSettingProcessedImage = false
            currentLoadedUri = null
            invalidate()
        }
    }
    // KMK <--

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    // KMK -->
    /**
     * Check if the image can be panned up
     */
    fun canPanUp(): Boolean = canPan { it.top }

    /**
     * Check if the image can be panned down
     */
    fun canPanDown(): Boolean = canPan { it.bottom }
    // KMK <--

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 1
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    // KMK -->
    /**
     * Pans the image down by a screen's height worth.
     */
    fun panDown() {
        pan { center, view -> center.also { it.y += view.height / view.scale } }
    }

    /**
     * Pans the image up by a screen's height worth.
     */
    fun panUp() {
        pan { center, view -> center.also { it.y -= view.height / view.scale } }
    }
    // KMK <--

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->

            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    // KMK -->
    protected fun setProcessedSource(
        file: java.io.File,
        bitmap: Bitmap? = null,
        transformedSource: BufferedSource? = null,
    ) {
        val uriString = file.toURI().toString()
        updateStatus(context.stringResource(KMR.strings.reader_status_processed))

        if (transformedSource != null) {
            val activeView = pageView as? SubsamplingScaleImageView
            if (activeView != null) {
                animateProcessedSwap(activeView, config) {
                    setImage(ImageSource.inputStream(transformedSource.inputStream()))
                }
            } else {
                val previewBitmap = try {
                    android.graphics.BitmapFactory.decodeStream(transformedSource.peek().inputStream())
                } catch (_: Exception) {
                    null
                }

                if (previewBitmap != null) {
                    enhancedOverlay.scaleType = ImageView.ScaleType.FIT_CENTER
                    enhancedOverlay.bringToFront()
                    enhancedOverlay.setImageBitmap(previewBitmap)
                    enhancedOverlay.alpha = 1f
                    enhancedOverlay.isVisible = true
                    statusView.bringToFront()
                    enhancedBitmap = previewBitmap
                    isSettingProcessedImage = true
                    pageView?.alpha = 0f
                } else {
                    pageView?.alpha = 1f
                    enhancedOverlay.animate().cancel()
                    enhancedOverlay.setImageBitmap(null)
                    enhancedOverlay.isVisible = false
                    enhancedBitmap?.recycle()
                    enhancedBitmap = null
                    isSettingProcessedImage = false
                }
            }
            currentLoadedUri = uriString
            isVisible = true
            return
        }

        if (bitmap != null) {
            val activeView = pageView as? SubsamplingScaleImageView
            if (activeView != null) {
                animateProcessedSwap(activeView, config) {
                    val uri = android.net.Uri.fromFile(file)
                    setImage(ImageSource.uri(context, uri))
                }
                currentLoadedUri = uriString
                isVisible = true
                return
            }
            enhancedOverlay.scaleType = ImageView.ScaleType.FIT_CENTER
            enhancedOverlay.bringToFront()
            enhancedOverlay.setImageBitmap(bitmap)
            enhancedOverlay.alpha = 1f
            enhancedOverlay.isVisible = true
            statusView.bringToFront()
            enhancedBitmap = bitmap
            isSettingProcessedImage = true
            pageView?.alpha = 0f
        }

        val uri = android.net.Uri.fromFile(file)
        (pageView as? SubsamplingScaleImageView)?.setImage(ImageSource.uri(context, uri))
        currentLoadedUri = uriString
        isVisible = true
    }

    private fun enhancementVariant(): String {
        return enhancementVariantOverride ?: readerPage?.enhancementKeySuffix.orEmpty()
    }

    private fun buildEnhancementDataProvider(
        streamFn: (() -> java.io.InputStream)? = null,
        originalData: Any? = null,
    ): (() -> Any?)? {
        fun buffered(stream: (() -> java.io.InputStream)?): (() -> Any?)? {
            return stream?.let { source ->
                {
                    try {
                        source().use { input -> Buffer().readFrom(input) }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        enhancementStreamOverride?.let { enhancedStream ->
            return buffered(enhancedStream)
        }

        readerPage?.let { page ->
            return buffered(page.stream) ?: page.imageUrl?.let { { it } }
        }

        return when (originalData) {
            is ReaderPage -> buffered(originalData.stream) ?: originalData.imageUrl?.let { { it } }
            null -> buffered(streamFn)
            else -> buffered(streamFn) ?: { originalData }
        }
    }

    private fun enqueueEnhancement(
        mId: Long,
        cId: Long,
        pIdx: Int,
        highPriority: Boolean,
        streamFn: (() -> java.io.InputStream)? = null,
        originalData: Any? = null,
    ) {
        val dataProvider = buildEnhancementDataProvider(streamFn, originalData) ?: return
        ImageEnhancer.enhanceLazy(
            context = context.applicationContext,
            mangaId = mId,
            chapterId = cId,
            pageIndex = pIdx,
            highPriority = highPriority,
            pageVariant = enhancementVariant(),
            dataProvider = dataProvider,
        )
    }

    fun promoteEnhancementRequest(highPriority: Boolean = true) {
        if (!realCuganEnabled) return

        val mId = readerPage?.chapter?.chapter?.manga_id ?: mangaId
        val cId = readerPage?.chapter?.chapter?.id ?: chapterId
        val pIdx = readerPage?.index ?: pageIndex
        if (pIdx < 0 || mId == -1L || cId == -1L) return

        ImageEnhancementCache.init(context)
        val configHash = ImageEnhancementCache.getConfigHash(
            noise = realCuganNoiseLevel,
            scale = realCuganScale,
            model = realCuganModel,
            realEsrganStyle = preferences.realEsrganStyle().get(),
            maxWidth = realCuganMaxSizeWidth,
            maxHeight = realCuganMaxSizeHeight,
            skipMaxWidth = realCuganSkipMaxSizeWidth,
            skipMaxHeight = realCuganSkipMaxSizeHeight,
            tileSize = tileSize,
            precision = preferences.realCuganPrecision().get(),
            fp16Arithmetic = preferences.realCuganFp16Arithmetic().get(),
            processingBackend = preferences.realCuganProcessingBackend().get(),
        )
        val pageVariant = enhancementVariant()

        if (ImageEnhancementCache.getCachedImage(mId, cId, pIdx, configHash, pageVariant) != null) return
        if (ImageEnhancementCache.isSkipped(mId, cId, pIdx, configHash, pageVariant)) return

        enqueueEnhancement(mId, cId, pIdx, highPriority)
    }

    private fun requeueEnhancement(
        mId: Long,
        cId: Long,
        pIdx: Int,
        triggerData: Any?,
        streamFn: (() -> java.io.InputStream)? = null,
        forceCurrentPage: Boolean = false,
    ) {
        val dataProvider = buildEnhancementDataProvider(streamFn, triggerData)

        if (dataProvider == null) {
            logcat(LogPriority.WARN) {
                "ReaderPageImageView: Unable to re-enqueue page $pIdx because source data is unavailable"
            }
            return
        }

        val isCurrent = forceCurrentPage || pIdx == currentGlobalPageIndex || pIdx == ImageEnhancer.targetPageIndex
        logcat(LogPriority.WARN) {
            "ReaderPageImageView: Re-enqueueing page $pIdx after invalid enhanced cache (current=$isCurrent)"
        }

        ImageEnhancer.enhanceLazy(
            context = context.applicationContext,
            mangaId = mId,
            chapterId = cId,
            pageIndex = pIdx,
            highPriority = isCurrent,
            pageVariant = enhancementVariant(),
            dataProvider = dataProvider,
        )
    }

    private suspend fun healInvalidEnhancedCache(
        mId: Long,
        cId: Long,
        pIdx: Int,
        configHash: String,
        triggerData: Any?,
        streamFn: (() -> java.io.InputStream)? = null,
        forceCurrentPage: Boolean = false,
    ) {
        val pageVariant = enhancementVariant()
        val removed = ImageEnhancementCache.removeCachedImage(mId, cId, pIdx, configHash, pageVariant)
        logcat(if (removed) LogPriority.WARN else LogPriority.ERROR) {
            "ReaderPageImageView: Invalid enhanced cache for page $pIdx/$pageVariant removed=$removed"
        }

        withUIContext {
            pageView?.alpha = 1f
            enhancedOverlay.setImageBitmap(null)
            enhancedOverlay.isVisible = false
            enhancedBitmap?.recycle()
            enhancedBitmap = null
            isSettingProcessedImage = false
            updateStatus(context.stringResource(KMR.strings.reader_status_processing))
        }

        requeueEnhancement(mId, cId, pIdx, triggerData, streamFn, forceCurrentPage)
    }
    // KMK <--

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        // KMK -->
        clearProcessedSwapView()
        // KMK <--
        removeView(pageView)

        pageView = createSubsamplingPageView()
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    // KMK -->
    private fun createSubsamplingPageView(): SubsamplingScaleImageView {
        // KMK <--
        return if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }.apply {
            setMaxTileSize(ImageUtil.hardwareBitmapThreshold)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        // Not used
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        // KMK -->
    }
    // KMK <--

    private fun SubsamplingScaleImageView.setupZoom(config: Config?) {
        // 5x zoom
        maxScale = scale * MAX_ZOOM_SCALE
        // KMK -->
        if (config?.disableZoomIn == true) {
            isZoomEnabled = false
        } else {
            if (config?.doubleTapZoom == false) {
                setDoubleTapZoomScale(scale)
            } else {
                // KMK <--
                setDoubleTapZoomScale(scale * 2)
            }
        }

        when (config?.zoomStartPosition) {
            ZoomStartPosition.LEFT -> setScaleAndCenter(scale, PointF(0F, 0F))
            ZoomStartPosition.RIGHT -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0F))
            ZoomStartPosition.CENTER -> setScaleAndCenter(scale, center)
            null -> {}
        }
    }

    private fun setNonAnimatedImage(
        data: Any,
        config: Config,
        streamFn: (() -> java.io.InputStream)? = null,
    ) = (pageView as? SubsamplingScaleImageView)?.apply {
        setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
        setMinimumScaleType(config.minimumScaleType)
        setMinimumDpi(1) // Just so that very small image will be fit for initial load
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    setupZoom(config)
                    if (isVisibleOnScreen()) landscapeZoom(true)
                    this@ReaderPageImageView.onImageLoaded()
                }

                override fun onImageLoadError(e: Exception) {
                    this@ReaderPageImageView.onImageLoadError(e)
                }
            },
        )

        when (data) {
            is BitmapDrawable -> {
                setImage(ImageSource.bitmap(data.bitmap))
                // KMK -->
                processImageHelper(originalData = data.bitmap)
                // KMK <--
                isVisible = true
            }
            is BufferedSource -> {
                if (!isWebtoon || alwaysDecodeLongStripWithSSIV) {
                    setHardwareConfig(ImageUtil.canUseHardwareBitmap(data))
                    setImage(ImageSource.inputStream(data.inputStream()))
                    isVisible = true
                    // KMK -->
                    // Enhancement Logic for Stream Sources
                    if (realCuganEnabled && pageIndex >= 0 && mangaId != -1L) {
                        processImageHelper(streamFn = streamFn)
                    }
                    // KMK <--
                    return@apply
                }

                ImageRequest.Builder(context)
                    .data(data)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .target(
                        onSuccess = { result ->
                            val image = result as BitmapImage
                            setImage(ImageSource.bitmap(image.bitmap))
                            isVisible = true
                        },
                    )
                    .listener(
                        onError = { _, result ->
                            onImageLoadError(result.throwable)
                        },
                    )
                    .size(ViewSizeResolver(this@ReaderPageImageView))
                    .precision(Precision.INEXACT)
                    .cropBorders(config.cropBorders)
                    .customDecoder(true)
                    .crossfade(false)
                    .build()
                    .let(context.imageLoader::enqueue)

                // KMK -->
                // Enhancement Logic for Stream Sources (webtoon long-strip decode path)
                if (realCuganEnabled && pageIndex >= 0 && mangaId != -1L) {
                    processImageHelper(streamFn = streamFn)
                }
                // KMK <--
            }
            else -> {
                throw IllegalArgumentException("Not implemented for class ${data::class.simpleName}")
            }
        }
    }

    // KMK -->
    private fun SubsamplingScaleImageView.processImageHelper(
        streamFn: (() -> java.io.InputStream)? = null,
        originalData: Any? = null,
    ) {
        if (isSettingProcessedImage || !realCuganEnabled || !enhancementDisplayEnabled) {
            updateStatus(if (realCuganEnabled && enhancementDisplayEnabled) null else context.stringResource(KMR.strings.reader_status_raw))
            return
        }

        // Initialize IDs from readerPage if available, otherwise use properties
        val mId = readerPage?.chapter?.chapter?.manga_id ?: mangaId
        val cId = readerPage?.chapter?.chapter?.id ?: chapterId
        val pIdx = readerPage?.index ?: pageIndex

        if (pIdx < 0 || mId == -1L || cId == -1L) {
            logcat(LogPriority.DEBUG) { "ReaderPageImageView: Skipping enhancement, invalid IDs (m=$mId, c=$cId, p=$pIdx)" }
            return
        }

        ImageEnhancementCache.init(context)
        val configHash = ImageEnhancementCache.getConfigHash(
            noise = realCuganNoiseLevel,
            scale = realCuganScale,
            model = realCuganModel,
            realEsrganStyle = preferences.realEsrganStyle().get(),
            maxWidth = realCuganMaxSizeWidth,
            maxHeight = realCuganMaxSizeHeight,
            skipMaxWidth = realCuganSkipMaxSizeWidth,
            skipMaxHeight = realCuganSkipMaxSizeHeight,
            tileSize = tileSize,
            precision = preferences.realCuganPrecision().get(),
            fp16Arithmetic = preferences.realCuganFp16Arithmetic().get(),
            processingBackend = preferences.realCuganProcessingBackend().get(),
        )
        val pageVariant = enhancementVariant()

        val cachedFile = ImageEnhancementCache.getCachedImage(mId, cId, pIdx, configHash, pageVariant)
        if (cachedFile != null) {
            logcat(LogPriority.DEBUG) { "ReaderPageImageView: Page $pIdx found in cache on first check: ${cachedFile.absolutePath}" }
            val transformedSource = enhancedImageSourceFactory?.invoke(cachedFile)
            if (transformedSource != null) {
                setProcessedSource(cachedFile, transformedSource = transformedSource)
            } else {
                val bitmap = decodeEnhancedBitmap(cachedFile)
                if (bitmap != null) {
                    val uri = android.net.Uri.fromFile(cachedFile)
                    setImage(ImageSource.uri(context, uri))
                    currentLoadedUri = cachedFile.toURI().toString()
                    isVisible = true
                    updateStatus(context.stringResource(KMR.strings.reader_status_processed))
                } else {
                    viewScope.launchIO {
                        healInvalidEnhancedCache(
                            mId = mId,
                            cId = cId,
                            pIdx = pIdx,
                            configHash = configHash,
                            triggerData = originalData,
                            streamFn = streamFn,
                            forceCurrentPage = pIdx == currentGlobalPageIndex,
                        )
                        startEnhancementPolling(mId, cId, pIdx, configHash, originalData, streamFn)
                    }
                }
            }
            return
        }

        if (ImageEnhancementCache.isSkipped(mId, cId, pIdx, configHash, pageVariant)) {
            logcat(LogPriority.DEBUG) { "ReaderPageImageView: Page $pIdx marked as skipped, showing RAW" }
            updateStatus(context.stringResource(KMR.strings.reader_status_raw))
            return
        }

        logcat(LogPriority.DEBUG) { "ReaderPageImageView: Page $pIdx NOT in cache, starting monitoring (m=$mId, c=$cId, config=$configHash)" }

        // Trigger enhancement if it's not already in progress
        val triggerDataProvider = buildEnhancementDataProvider(streamFn, originalData)

        if (triggerDataProvider != null) {
            // Use High Priority if this is the current target page (the one user is viewing)
            // This ensures re-queued pages are processed immediately, not stuck in low priority
            val isCurrentPage = pIdx == ImageEnhancer.targetPageIndex
            val isInitialTargetPage = currentGlobalPageIndex < 0 && isCurrentPage
            val canEnqueueNow = currentGlobalPageIndex >= 0 || isInitialTargetPage

            if (canEnqueueNow) {
                logcat(LogPriority.DEBUG) { "ReaderPageImageView: Triggering enhancement for page $pIdx. isCurrentPage=$isCurrentPage (target=${ImageEnhancer.targetPageIndex})" }

                ImageEnhancer.enhanceLazy(
                    context = context.applicationContext,
                    mangaId = mId,
                    chapterId = cId,
                    pageIndex = pIdx,
                    highPriority = isCurrentPage,
                    pageVariant = pageVariant,
                    dataProvider = triggerDataProvider,
                )
            } else {
                logcat(LogPriority.DEBUG) {
                    "ReaderPageImageView: Delaying enhancement enqueue for page $pIdx until initial target page ${ImageEnhancer.targetPageIndex} starts"
                }
            }
        }

        // Simplified polling for the enhanced image in cache
        startEnhancementPolling(mId, cId, pIdx, configHash, originalData, streamFn)
    }

    /**
     * Start or restart the polling job to monitor enhancement progress and update status.
     */
    private fun startEnhancementPolling(
        mId: Long,
        cId: Long,
        pIdx: Int,
        configHash: String,
        originalData: Any? = null,
        streamFn: (() -> java.io.InputStream)? = null,
    ) {
        processingJob?.cancel()
        processingJob = viewScope.launchIO {
            try {
                val pageVariant = enhancementVariant()
                var attempts = 0
                var wasEnhancing = false
                while (attempts < 120 && isActive) {
                    if (ImageEnhancementCache.isSkipped(mId, cId, pIdx, configHash, pageVariant)) {
                        withUIContext { updateStatus(context.stringResource(KMR.strings.reader_status_raw)) }
                        return@launchIO
                    }
                    val file = ImageEnhancementCache.getCachedImage(mId, cId, pIdx, configHash, pageVariant)
                    if (file != null) {
                        logcat(LogPriority.DEBUG) { "ReaderPageImageView: Page $pIdx/$pageVariant found in cache during polling: ${file.absolutePath}" }
                        val transformedSource = enhancedImageSourceFactory?.invoke(file)
                        if (transformedSource != null) {
                            withUIContext {
                                setProcessedSource(file, transformedSource = transformedSource)
                            }
                            return@launchIO
                        }

                        val bitmap = decodeEnhancedBitmap(file)

                        if (bitmap != null) {
                            withUIContext {
                                setProcessedSource(file, bitmap = bitmap)
                            }
                        } else {
                            healInvalidEnhancedCache(
                                mId = mId,
                                cId = cId,
                                pIdx = pIdx,
                                configHash = configHash,
                                triggerData = originalData,
                                streamFn = streamFn,
                                forceCurrentPage = pIdx == currentGlobalPageIndex,
                            )
                            delay(200)
                            continue
                        }

                        return@launchIO
                    }

                    // Check progress status
                    val pid = Waifu2x.getProgressId()
                    if (pid == pIdx) {
                        wasEnhancing = true
                        val rawProgress = Waifu2x.getProgressPercent()
                        if (rawProgress in 0..100) {
                            updateStatus(context.stringResource(KMR.strings.reader_status_enhancing_progress, rawProgress))
                        } else {
                            val dots = (rawProgress % 3).let { if (it < 0) -it else it } + 1
                            updateStatus(context.stringResource(KMR.strings.reader_status_enhancing) + ".".repeat(dots))
                        }
                    } else if (ImageEnhancer.hasRequest(mId, cId, pIdx, pageVariant)) {
                        if (!wasEnhancing) {
                            updateStatus(context.stringResource(KMR.strings.reader_status_queued))
                        } else {
                            updateStatus(context.stringResource(KMR.strings.reader_status_finishing))
                        }
                    } else {
                        // Not in queue, not cached - might need to re-enqueue
                        val current = currentGlobalPageIndex
                        val shouldHeal = pIdx >= current && pIdx <= current + preloadSize
                        if (shouldHeal && !ImageEnhancementCache.isSkipped(mId, cId, pIdx, configHash, pageVariant)) {
                            logcat(LogPriority.WARN) { "ReaderPageImageView: Polling re-enqueue page $pIdx/$pageVariant (cur=$current)" }
                            val isCurrent = pIdx == current
                            enqueueEnhancement(mId, cId, pIdx, highPriority = isCurrent, originalData = originalData, streamFn = streamFn)
                        } else {
                            updateStatus(context.stringResource(KMR.strings.reader_status_raw))
                            delay(2000)
                            return@launchIO
                        }
                    }

                    delay(500)
                    attempts++
                }
            } catch (_: CancellationException) {
                return@launchIO
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Error in polling for page $pIdx" }
            }
        }
    }
    // KMK <--

    // KMK -->
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        processingJob?.cancel()
        processingJob = null

        enhancedOverlay.setImageBitmap(null)
        enhancedBitmap?.recycle()
        enhancedBitmap = null
        isSettingProcessedImage = false
        currentLoadedUri = null
        clearProcessedSwapView()
    }
    // KMK <--

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context)
        }.apply {
            adjustViewBounds = true

            if (this is PhotoView) {
                setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                // Force 2 scale levels on double tap
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (scale > 1F) {
                                setScale(1F, e.x, e.y, true)
                            } else {
                                setScale(2F, e.x, e.y, true)
                            }
                            return true
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            this@ReaderPageImageView.onViewClicked()
                            return super.onSingleTapConfirmed(e)
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ ->
                    this@ReaderPageImageView.onScaleChanged(scale)
                }
            }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun setAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? AppCompatImageView)?.apply {
        if (this is PhotoView) {
            setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
        }

        val request = ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(context.resources)
                    setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                    isVisible = true
                    this@ReaderPageImageView.onImageLoaded()
                },
            )
            .listener(
                onError = { _, result ->
                    onImageLoadError(result.throwable)
                },
            )
            .crossfade(false)
            // KMK -->
            .allowHardware(false) // Disable hardware bitmaps for GIFs
            // KMK <--
            .build()
        context.imageLoader.enqueue(request)
    }

    private fun Int.getSystemScaledDuration(): Int {
        return (this * context.animatorDurationScale).toInt().coerceAtLeast(1)
    }

    /**
     * All of the config except [zoomDuration] will only be used for non-animated image.
     */
    data class Config(
        val zoomDuration: Int,
        val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
        // KMK -->
        val disableZoomIn: Boolean = false,
        val doubleTapZoom: Boolean = true,
        val landscapeZoomScaleType: LandscapeZoomScaleType = LandscapeZoomScaleType.FIT,
        // KMK <--
    )

    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }

    companion object {
        // KMK -->
        /** Page index of the page currently shown, used by enhancement jobs to self-heal. */
        @Volatile var currentGlobalPageIndex: Int = -1
        // KMK <--
    }
}

private const val MAX_ZOOM_SCALE = 5F

// KMK -->
private const val PROCESSED_SWAP_DURATION_MS = 280L
// KMK <--
