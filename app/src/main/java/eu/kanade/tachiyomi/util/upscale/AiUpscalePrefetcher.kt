package eu.kanade.tachiyomi.util.upscale

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.log.xLogD
import exh.log.xLogE
import exh.log.xLogW
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Buffer
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Preload the upscaling of the N pages following the current one into cache,
 * while the user is still reading the current one (idle time), instead of
 * starting on demand exactly when the page becomes visible.
 *
 * Dedicated scope, long-lasting by design (must survive recycling of the individual holders)
 */
object AiUpscalePrefetcher {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        xLogE("fillLoop stopped due to unhandled exception", throwable)
    }
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val requested = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var currentChapterPages: List<ReaderPage>? = null

    @Volatile private var nextChapterProvider: (() -> List<ReaderPage>?)? = null

    @Volatile private var currentIndex: Int = -1

    @Volatile private var aheadCount: Int = 2

    @Volatile private var alreadyCoveredAhead: Int = 0

    @Volatile private var targetWidth: Int = 0
    private var fillJob: Job? = null

    /*
     * Returns the page at the required offset from the current position,
     * crossing the chapter boundary if necessary. If the offset falls in the
     * next chapter but this one is not loaded yet (pages == null), returns
     * null: the caller will retry it on the next turn of the loop, it's not a
     * definitive failure.
     */
    private fun pageAtOffset(offset: Int): ReaderPage? {
        val curPages = currentChapterPages ?: return null
        val idx = currentIndex + offset
        if (idx < curPages.size) return curPages.getOrNull(idx)

        val overflow = idx - curPages.size
        val nextPages = nextChapterProvider?.invoke() ?: return null
        return nextPages.getOrNull(overflow)
    }
    private suspend fun tryFillNextGap(): Boolean {
        if (currentChapterPages == null) {
            xLogD("tryFillNextGap: currentChapterPages is null")
            return false
        }

        val startOffset = alreadyCoveredAhead + 1
        if (startOffset > aheadCount) return false

        for (offset in startOffset..aheadCount) {
            val nextPage = pageAtOffset(offset) ?: continue
            val key = "${nextPage.chapter.chapter.id}_${nextPage.index}"
            if (requested.contains(key)) continue

            // Pages across the chapter boundary belong to a different pageLoader
            val loader = nextPage.chapter.pageLoader
            if (loader == null) {
                xLogD("offset=$offset page ${nextPage.index}: pageLoader not ready (chapter not started)")
                continue
            }

            try {
                prefetchScope.launch(Dispatchers.IO) {
                    try {
                        loader.loadPage(nextPage)
                    } catch (e: Throwable) {
                        xLogW("failed loadPage for page ${nextPage.index}", e)
                    }
                }

                val readyState = withTimeoutOrNull(15_000.milliseconds) {
                    nextPage.statusFlow.first { it is Page.State.Ready || it is Page.State.Error }
                }

                if (readyState !is Page.State.Ready) {
                    xLogD("offset=$offset page ${nextPage.index}: not ready within timeout (state=$readyState)")
                    continue
                }

                val streamFn = nextPage.stream
                if (streamFn == null) {
                    xLogW("offset=$offset page ${nextPage.index}: Ready but null stream")
                    requested.add(key)
                    continue
                }

                xLogD("offset=$offset page ${nextPage.index}: starting upscale")
                val bytes = withContext(Dispatchers.IO) { streamFn().use { it.readBytes() } }
                if (!ImageUtil.isAnimatedAndSupported(Buffer().write(bytes))) {
                    AiUpscaleCache.getOrUpscale(
                        chapterId = nextPage.chapter.chapter.id,
                        pageIndex = nextPage.index,
                        source = Buffer().write(bytes),
                        targetWidth = targetWidth,
                        priority = UpscalePriorityGate.Priority.PREFETCH,
                    )
                }
                requested.add(key)
                xLogD("offset=$offset page ${nextPage.index}: completed")
                return true
            } catch (e: Throwable) {
                xLogE("offset=$offset page ${nextPage.index}: exception", e)
            }
        }
        xLogD("no processable page (currentIndex=$currentIndex, aheadCount=$aheadCount)")
        return false
    }
    private suspend fun fillLoop() {
        xLogD("fillLoop started")
        while (currentCoroutineContext().isActive) {
            val processedSomething = try {
                tryFillNextGap()
            } catch (e: Throwable) {
                xLogE("tryFillNextGap threw an exception", e)
                false
            }
            if (!processedSomething) {
                delay(500.milliseconds)
            }
        }
        xLogD("fillLoop terminated")
    }

    /**
     * Update the current read position. It does not directly start the
     * prefetch of N pages: it updates only the state that the continuous
     * loop (started only once) reads at each iteration. Call
     * at every page change, not just the first.
     * @param nextChapterProvider provides the pages of the next chapter, if
     * available. Passed as a lambda because the
     * chapter may not be loaded yet at the time of this call
     * but become one while the loop keeps spinning
     */
    fun updatePosition(
        current: ReaderPage,
        aheadCount: Int,
        targetWidth: Int,
        alreadyCoveredAhead: Int = 0,
        nextChapterProvider: () -> List<ReaderPage>? = { null },
    ) {
        val upscalePrefs = Injekt.get<ReaderPreferences>()
        if (!upscalePrefs.aiUpscaleEnabled().get()) return

        currentChapterPages = current.chapter.pages
        this.nextChapterProvider = nextChapterProvider
        currentIndex = current.index
        this.aheadCount = aheadCount
        this.alreadyCoveredAhead = alreadyCoveredAhead
        this.targetWidth = targetWidth

        if (fillJob?.isActive != true) {
            fillJob = prefetchScope.launch { fillLoop() }
        }
    }

    // To call when chapter is changed, avoiding the Set to grow indefinitely
    fun clear() {
        requested.clear()
        fillJob?.cancel()
        fillJob = null
        currentChapterPages = null
        nextChapterProvider = null
        currentIndex = -1
    }
}
