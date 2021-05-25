package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.drawable.Animatable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressBar
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig.ZoomType
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * View of the ViewPager that contains a page of a chapter.
 */
@SuppressLint("ViewConstructor")
class PagerPageHolder(
    val viewer: PagerViewer,
    val page: ReaderPage,
    private var extraPage: ReaderPage? = null
) : FrameLayout(viewer.activity), ViewPagerAdapter.PositionableView {

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page to extraPage

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressBar = createProgressBar()

    /**
     * Image view that supports subsampling on zoom.
     */
    private var subsamplingImageView: SubsamplingScaleImageView? = null

    /**
     * Simple image view only used on GIFs.
     */
    private var imageView: ImageView? = null

    /**
     * Retry button used to allow retrying.
     */
    private var retryButton: PagerButton? = null

    /**
     * Error layout to show when the image fails to decode.
     */
    private var decodeErrorLayout: ViewGroup? = null

    /**
     * Subscription for status changes of the page.
     */
    private var statusSubscription: Subscription? = null

    /**
     * Subscription for progress changes of the page.
     */
    private var progressSubscription: Subscription? = null

    /**
     * Subscription for status changes of the page.
     */
    private var extraStatusSubscription: Subscription? = null

    /**
     * Subscription for progress changes of the page.
     */
    private var extraProgressSubscription: Subscription? = null

    /**
     * Subscription used to read the header of the image. This is needed in order to instantiate
     * the appropiate image view depending if the image is animated (GIF).
     */
    private var readImageHeaderSubscription: Subscription? = null

    // SY -->
    var status: Int = 0
    var extraStatus: Int = 0
    var progress: Int = 0
    var extraProgress: Int = 0
    private var skipExtra = false
    var scope: CoroutineScope? = null
    // SY <--

    init {
        addView(progressBar)
        scope = CoroutineScope(Job() + Dispatchers.Default)
        observeStatus()
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unsubscribeProgress(1)
        unsubscribeProgress(2)
        unsubscribeStatus(1)
        unsubscribeStatus(2)
        unsubscribeReadImageHeader()
        subsamplingImageView?.setOnImageEventListener(null)
    }

    /**
     * Observes the status of the page and notify the changes.
     *
     * @see processStatus
     */
    private fun observeStatus() {
        statusSubscription?.unsubscribe()

        val loader = page.chapter.pageLoader ?: return
        statusSubscription = loader.getPage(page)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                status = it
                processStatus(it)
            }

        val extraPage = extraPage ?: return
        val loader2 = extraPage.chapter.pageLoader ?: return
        extraStatusSubscription = loader2.getPage(extraPage)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                extraStatus = it
                processStatus2(it)
            }
    }

    /**
     * Observes the progress of the page and updates view.
     */
    private fun observeProgress() {
        progressSubscription?.unsubscribe()

        progressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS)
            .map { page.progress }
            .distinctUntilChanged()
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { value -> progressBar.setProgress(value) }
    }

    private fun observeProgress2() {
        extraProgressSubscription?.unsubscribe()
        val extraPage = extraPage ?: return
        extraProgressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS)
            .map { extraPage.progress }
            .distinctUntilChanged()
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { value ->
                extraProgress = value
                progressBar.setProgress(((progress + extraProgress) / 2 * 0.95f).roundToInt())
            }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus(status: Int) {
        when (status) {
            Page.QUEUE -> setQueued()
            Page.LOAD_PAGE -> setLoading()
            Page.DOWNLOAD_IMAGE -> {
                observeProgress()
                setDownloading()
            }
            Page.READY -> {
                if (extraStatus == Page.READY || extraPage == null) {
                    setImage()
                }
                unsubscribeProgress(1)
            }
            Page.ERROR -> {
                setError()
                unsubscribeProgress(1)
            }
        }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus2(status: Int) {
        when (status) {
            Page.QUEUE -> setQueued()
            Page.LOAD_PAGE -> setLoading()
            Page.DOWNLOAD_IMAGE -> {
                observeProgress2()
                setDownloading()
            }
            Page.READY -> {
                if (this.status == Page.READY) {
                    setImage()
                }
                unsubscribeProgress(2)
            }
            Page.ERROR -> {
                setError()
                unsubscribeProgress(2)
            }
        }
    }

    /**
     * Unsubscribes from the status subscription.
     */
    private fun unsubscribeStatus(page: Int) {
        val subscription = if (page == 1) statusSubscription else extraStatusSubscription
        subscription?.unsubscribe()
        if (page == 1) statusSubscription = null else extraStatusSubscription = null
    }

    /**
     * Unsubscribes from the progress subscription.
     */
    private fun unsubscribeProgress(page: Int) {
        val subscription = if (page == 1) progressSubscription else extraProgressSubscription
        subscription?.unsubscribe()
        if (page == 1) progressSubscription = null else extraProgressSubscription = null
    }

    /**
     * Unsubscribes from the read image header subscription.
     */
    private fun unsubscribeReadImageHeader() {
        readImageHeaderSubscription?.unsubscribe()
        readImageHeaderSubscription = null
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressBar.isVisible = true
        retryButton?.isVisible = false
        decodeErrorLayout?.isVisible = false
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressBar.isVisible = true
        retryButton?.isVisible = false
        decodeErrorLayout?.isVisible = false
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        progressBar.isVisible = true
        retryButton?.isVisible = false
        decodeErrorLayout?.isVisible = false
    }

    /**
     * Called when the page is ready.
     */
    private fun setImage() {
        progressBar.isVisible = true
        progressBar.isVisible = true
        if (extraPage == null) {
            progressBar.completeAndFadeOut()
        } else {
            progressBar.setProgress(95)
        }
        retryButton?.isVisible = false
        decodeErrorLayout?.isVisible = false

        unsubscribeReadImageHeader()
        val streamFn = page.stream ?: return
        val streamFn2 = extraPage?.stream

        var openStream: InputStream? = null
        readImageHeaderSubscription = Observable
            .fromCallable {
                val stream = streamFn().buffered(16)
                // SY -->
                val stream2 = if (extraPage != null) streamFn2?.invoke()?.buffered(16) else null
                openStream = if (viewer.config.dualPageSplit) {
                    process(item.first, stream)
                } else {
                    mergePages(stream, stream2)
                }
                // SY <--

                ImageUtil.isAnimatedAndSupported(stream)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { isAnimated ->
                if (!isAnimated) {
                    initSubsamplingImageView().apply {
                        if (viewer.config.automaticBackground) {
                            background = ImageUtil.chooseBackground(context, openStream!!)
                        }
                        setImage(ImageSource.inputStream(openStream!!))
                    }
                } else {
                    initImageView().setImage(openStream!!)
                }
            }
            // Keep the Rx stream alive to close the input stream only when unsubscribed
            .flatMap { Observable.never<Unit>() }
            .doOnUnsubscribe { openStream?.close() }
            .subscribe({}, {})
    }

    private fun process(page: ReaderPage, imageStream: InputStream): InputStream {
        if (!viewer.config.dualPageSplit) {
            return imageStream
        }

        if (page is InsertPage) {
            return splitInHalf(imageStream)
        }

        val isDoublePage = ImageUtil.isDoublePage(imageStream)
        if (!isDoublePage) {
            return imageStream
        }

        onPageSplit(page)

        return splitInHalf(imageStream)
    }

    private fun mergePages(imageStream: InputStream, imageStream2: InputStream?): InputStream {
        imageStream2 ?: return imageStream
        if (page.fullPage) return imageStream
        if (ImageUtil.findImageType(imageStream) == ImageUtil.ImageType.GIF) {
            page.fullPage = true
            skipExtra = true
            return imageStream
        } else if (ImageUtil.findImageType(imageStream2) == ImageUtil.ImageType.GIF) {
            page.isolatedPage = true
            extraPage?.fullPage = true
            skipExtra = true
            return imageStream
        }
        val imageBytes = imageStream.readBytes()
        val imageBitmap = try {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            imageStream2.close()
            imageStream.close()
            page.fullPage = true
            skipExtra = true
            Timber.e("Cannot combine pages ${e.message}")
            return imageBytes.inputStream()
        }
        scope?.launchUI { progressBar.setProgress(96) }
        val height = imageBitmap.height
        val width = imageBitmap.width

        if (height < width) {
            imageStream2.close()
            imageStream.close()
            page.fullPage = true
            skipExtra = true
            return imageBytes.inputStream()
        }

        val imageBytes2 = imageStream2.readBytes()
        val imageBitmap2 = try {
            BitmapFactory.decodeByteArray(imageBytes2, 0, imageBytes2.size)
        } catch (e: Exception) {
            imageStream2.close()
            imageStream.close()
            extraPage?.fullPage = true
            skipExtra = true
            page.isolatedPage = true
            Timber.e("Cannot combine pages ${e.message}")
            return imageBytes.inputStream()
        }
        scope?.launchUI { progressBar.setProgress(97) }
        val height2 = imageBitmap2.height
        val width2 = imageBitmap2.width

        if (height2 < width2) {
            imageStream2.close()
            imageStream.close()
            extraPage?.fullPage = true
            page.isolatedPage = true
            skipExtra = true
            return imageBytes.inputStream()
        }
        val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)

        imageStream.close()
        imageStream2.close()
        return ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, viewer.config.pageCanvasColor) {
            scope?.launchUI {
                if (it == 100) {
                    progressBar.completeAndFadeOut()
                } else {
                    progressBar.setProgress(it)
                }
            }
        }
    }

    private fun splitInHalf(imageStream: InputStream): InputStream {
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

        return ImageUtil.splitInHalf(imageStream, side)
    }

    private fun onPageSplit(page: ReaderPage) {
        val newPage = InsertPage(page)
        viewer.onPageSplit(page, newPage)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressBar.isVisible = false
        initRetryButton().isVisible = true
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressBar.isVisible = false
    }

    /**
     * Called when an image fails to decode.
     */
    private fun onImageDecodeError() {
        progressBar.isVisible = false
        initDecodeErrorLayout().isVisible = true
    }

    /**
     * Creates a new progress bar.
     */
    @SuppressLint("PrivateResource")
    private fun createProgressBar(): ReaderProgressBar {
        return ReaderProgressBar(context, null).apply {
            val size = 48.dpToPx
            layoutParams = LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
        }
    }

    /**
     * Initializes a subsampling scale view.
     */
    private fun initSubsamplingImageView(): SubsamplingScaleImageView {
        if (subsamplingImageView != null) return subsamplingImageView!!

        val config = viewer.config

        subsamplingImageView = SubsamplingScaleImageView(context).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setMaxTileSize(viewer.activity.maxBitmapSize)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setDoubleTapZoomDuration(config.doubleTapAnimDuration)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumScaleType(config.imageScaleType)
            setMinimumDpi(90)
            setMinimumTileDpi(180)
            setCropBorders(config.imageCropBorders)
            setOnImageEventListener(
                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                    override fun onReady() {
                        when (config.imageZoomType) {
                            ZoomType.Left -> setScaleAndCenter(scale, PointF(0f, 0f))
                            ZoomType.Right -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0f))
                            ZoomType.Center -> setScaleAndCenter(scale, center.also { it?.y = 0f })
                        }
                        onImageDecoded()
                    }

                    override fun onImageLoadError(e: Exception) {
                        onImageDecodeError()
                    }
                }
            )
        }
        addView(subsamplingImageView)
        return subsamplingImageView!!
    }

    /**
     * Initializes an image view, used for GIFs.
     */
    private fun initImageView(): ImageView {
        if (imageView != null) return imageView!!

        imageView = PhotoView(context, null).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
            adjustViewBounds = true
            setZoomTransitionDuration(viewer.config.doubleTapAnimDuration)
            setScaleLevels(1f, 2f, 3f)
            // Force 2 scale levels on double tap
            setOnDoubleTapListener(
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (scale > 1f) {
                            setScale(1f, e.x, e.y, true)
                        } else {
                            setScale(2f, e.x, e.y, true)
                        }
                        return true
                    }
                }
            )
        }
        addView(imageView)
        return imageView!!
    }

    /**
     * Initializes a button to retry pages.
     */
    private fun initRetryButton(): PagerButton {
        if (retryButton != null) return retryButton!!

        retryButton = PagerButton(context, viewer).apply {
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setText(R.string.action_retry)
            setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }
        }
        addView(retryButton)
        return retryButton!!
    }

    /**
     * Initializes a decode error layout.
     */
    private fun initDecodeErrorLayout(): ViewGroup {
        if (decodeErrorLayout != null) return decodeErrorLayout!!

        val margins = 8.dpToPx

        val decodeLayout = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
        }
        decodeErrorLayout = decodeLayout

        TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(margins, margins, margins, margins)
            }
            gravity = Gravity.CENTER
            setText(R.string.decode_image_error)

            decodeLayout.addView(this)
        }

        PagerButton(context, viewer).apply {
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(margins, margins, margins, margins)
            }
            setText(R.string.action_retry)
            setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }

            decodeLayout.addView(this)
        }

        val imageUrl = page.imageUrl
        if (imageUrl.orEmpty().startsWith("http", true)) {
            PagerButton(context, viewer).apply {
                layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    setMargins(margins, margins, margins, margins)
                }
                setText(R.string.action_open_in_web_view)
                setOnClickListener {
                    val intent = WebViewActivity.newIntent(context, imageUrl!!)
                    context.startActivity(intent)
                }

                decodeLayout.addView(this)
            }
        }

        addView(decodeLayout)
        return decodeLayout
    }

    /**
     * Extension method to set a [stream] into this ImageView.
     */
    private fun ImageView.setImage(stream: InputStream) {
        val request = ImageRequest.Builder(context)
            .data(ByteBuffer.wrap(stream.readBytes()))
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    if (result is Animatable) {
                        result.start()
                    }
                    setImageDrawable(result)
                    onImageDecoded()
                },
                onError = {
                    onImageDecodeError()
                }
            )
            .crossfade(false)
            .build()
        context.imageLoader.enqueue(request)
    }
}
