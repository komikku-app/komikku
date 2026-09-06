package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Color
import androidx.annotation.ColorInt
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.LandscapeZoomScaleType
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.EdgeNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.KindlishNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import eu.kanade.tachiyomi.util.waifu2x.ImageEnhancementCache
import eu.kanade.tachiyomi.util.waifu2x.ImageEnhancer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Configuration used by pager viewers.
 */
class PagerConfig(
    private val viewer: PagerViewer,
    scope: CoroutineScope,
    readerPreferences: ReaderPreferences = Injekt.get(),
) : ViewerConfig(readerPreferences, scope) {

    var automaticBackground = false
        private set

    var dualPageSplitChangedListener: ((Boolean) -> Unit)? = null

    var reloadChapterListener: ((Boolean) -> Unit)? = null

    var imageScaleType = 1
        private set

    var imageZoomType = ReaderPageImageView.ZoomStartPosition.LEFT
        private set

    var imageCropBorders = false
        private set

    var navigateToPan = false
        private set

    var landscapeZoom = false
        private set

    // KMK -->
    var disableZoomIn = false
        private set

    var doubleTapZoom = true
        private set

    var landscapeZoomScaleType = LandscapeZoomScaleType.FIT
        private set
    // KMK <--

    // SY -->
    var usePageTransitions = false

    var shiftDoublePage = false

    var doublePages =
        readerPreferences.pageLayout().get() == PageLayout.DOUBLE_PAGES && !readerPreferences.dualPageSplitPaged().get()
        set(value) {
            field = value
            if (!value) {
                shiftDoublePage = false
            }
        }

    var invertDoublePages = false

    var autoDoublePages = readerPreferences.pageLayout().get() == PageLayout.AUTOMATIC

    @ColorInt
    var pageCanvasColor = Color.WHITE

    var centerMarginType = CenterMarginType.NONE
    // SY <--

    init {
        readerPreferences.readerTheme()
            .register(
                {
                    // SY -->
                    themeToColor(it)
                    // SY <--
                    automaticBackground = it == 3
                },
                {
                    imagePropertyChangedListener?.invoke()
                    // SY -->
                    themeToColor(it)
                    reloadChapterListener?.invoke(doublePages)
                    // SY <--
                },
            )

        readerPreferences.imageScaleType()
            .register({ imageScaleType = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.zoomStart()
            .register({ zoomTypeFromPreference(it) }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.cropBorders()
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.navigateToPan()
            .register({ navigateToPan = it })

        readerPreferences.landscapeZoom()
            .register({ landscapeZoom = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.navigationModePager()
            .register({ navigationMode = it }, { updateNavigation(navigationMode) })

        readerPreferences.pagerNavInverted()
            .register({ tappingInverted = it }, { navigator.invertMode = it })
        readerPreferences.pagerNavInverted().changes()
            .drop(1)
            .onEach { navigationModeChangedListener?.invoke() }
            .launchIn(scope)

        // KMK -->
        readerPreferences.smallerTapZone().changes()
            .drop(1)
            .onEach { updateNavigation(navigationMode) }
            .launchIn(scope)
        // KMK <--

        readerPreferences.dualPageSplitPaged()
            .register(
                { dualPageSplit = it },
                {
                    imagePropertyChangedListener?.invoke()
                    dualPageSplitChangedListener?.invoke(it)
                },
            )

        readerPreferences.dualPageInvertPaged()
            .register({ dualPageInvert = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageRotateToFit()
            .register(
                { dualPageRotateToFit = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.dualPageRotateToFitInvert()
            .register(
                { dualPageRotateToFitInvert = it },
                { imagePropertyChangedListener?.invoke() },
            )

        // KMK -->
        readerPreferences.pagedDisableZoomIn()
            .register(
                { disableZoomIn = it },
                { imagePropertyChangedListener?.invoke() },
            )
        readerPreferences.pagedDoubleTapZoomEnabled()
            .register(
                { doubleTapZoom = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.landscapeZoomType()
            .register(
                { landscapeZoomScaleType = it },
                { imagePropertyChangedListener?.invoke() },
            )
        // KMK <--

        // SY -->
        readerPreferences.pageTransitionsPager()
            .register({ usePageTransitions = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.pageLayout()
            .register(
                {
                    autoDoublePages = it == PageLayout.AUTOMATIC
                    if (!autoDoublePages) {
                        doublePages = it == PageLayout.DOUBLE_PAGES && dualPageSplit == false
                    }
                },
                {
                    autoDoublePages = it == PageLayout.AUTOMATIC
                    if (!autoDoublePages) {
                        doublePages = it == PageLayout.DOUBLE_PAGES && dualPageSplit == false
                    }
                    reloadChapterListener?.invoke(doublePages)
                },
            )

        readerPreferences.centerMarginType()
            .register({ centerMarginType = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.invertDoublePages()
            .register({ invertDoublePages = it && dualPageSplit == false }, { imagePropertyChangedListener?.invoke() })
        // SY <--

        // KMK --> Image enhancement (upscale) - refresh pages when changed
        readerPreferences.realCuganEnabled().changes()
            .drop(1)
            .onEach {
                ImageEnhancer.cancelAll("realCuganEnabled changed")
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganModel().changes()
            .drop(1)
            .onEach {
                ImageEnhancer.cancelAll("realCuganModel changed")
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realEsrganStyle().changes()
            .drop(1)
            .onEach {
                ImageEnhancer.cancelAll("realEsrganStyle changed")
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganNoiseLevel().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganNoiseLevel changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganScale().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganScale changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganMaxSizeWidth().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganMaxSizeWidth changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganMaxSizeHeight().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganMaxSizeHeight changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganSkipMaxSizeWidth().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganSkipMaxSizeWidth changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganSkipMaxSizeHeight().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganSkipMaxSizeHeight changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganTileSize().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganTileSize changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganPrecision().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganPrecision changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganProcessingBackend().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganProcessingBackend changed") }
            .debounce(500)
            .onEach {
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganFp16Arithmetic().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganFp16Arithmetic changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(viewer.activity)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganShowStatus().changes()
            .drop(1)
            .onEach {
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganPreloadSize().changes()
            .drop(1)
            .onEach {
                // No need to clear cache for preload size change
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)
        // KMK <--
    }

    private fun zoomTypeFromPreference(value: Int) {
        imageZoomType = when (value) {
            // Auto
            1 -> when (viewer) {
                is L2RPagerViewer -> ReaderPageImageView.ZoomStartPosition.LEFT
                is R2LPagerViewer -> ReaderPageImageView.ZoomStartPosition.RIGHT
                else -> ReaderPageImageView.ZoomStartPosition.CENTER
            }
            // Left
            2 -> ReaderPageImageView.ZoomStartPosition.LEFT
            // Right
            3 -> ReaderPageImageView.ZoomStartPosition.RIGHT
            // Center
            else -> ReaderPageImageView.ZoomStartPosition.CENTER
        }
    }

    override var navigator: ViewerNavigation = defaultNavigation()
        set(value) {
            field = value.also { it.invertMode = this.tappingInverted }
        }

    override fun defaultNavigation(): ViewerNavigation {
        return when (viewer) {
            is VerticalPagerViewer -> LNavigation()
            else -> RightAndLeftNavigation()
        }
    }

    override fun updateNavigation(navigationMode: Int) {
        navigator = when (navigationMode) {
            0 -> defaultNavigation()
            1 -> LNavigation()
            2 -> KindlishNavigation()
            3 -> EdgeNavigation()
            4 -> RightAndLeftNavigation()
            5 -> DisabledNavigation()
            else -> defaultNavigation()
        }
        navigationModeChangedListener?.invoke()
    }

    object CenterMarginType {
        const val NONE = 0
        const val DOUBLE_PAGE_CENTER_MARGIN = 1
        const val WIDE_PAGE_CENTER_MARGIN = 2
        const val DOUBLE_AND_WIDE_CENTER_MARGIN = 3
    }

    object PageLayout {
        const val SINGLE_PAGE = 0
        const val DOUBLE_PAGES = 1
        const val AUTOMATIC = 2
    }

    fun themeToColor(theme: Int) {
        pageCanvasColor = when (theme) {
            1 -> Color.BLACK
            2 -> 0x202125
            else -> Color.WHITE
        }
    }
}
