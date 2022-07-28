package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Color
import androidx.annotation.ColorInt
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.EdgeNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.KindlishNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import kotlinx.coroutines.CoroutineScope
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
    preferences: PreferencesHelper = Injekt.get(),
) : ViewerConfig(preferences, scope) {

    var theme = preferences.readerTheme().get()
        private set

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

    // SY -->
    var usePageTransitions = false

    var shiftDoublePage = false

    var doublePages = preferences.pageLayout().get() == PageLayout.DOUBLE_PAGES && !preferences.dualPageSplitPaged().get()
        set(value) {
            field = value
            if (!value) {
                shiftDoublePage = false
            }
        }

    var invertDoublePages = false

    var autoDoublePages = preferences.pageLayout().get() == PageLayout.AUTOMATIC

    @ColorInt
    var pageCanvasColor = Color.WHITE

    var centerMarginType = CenterMarginType.NONE

    // SY <--

    init {
        preferences.readerTheme()
            .register(
                {
                    theme = it
                    automaticBackground = it == 3
                },
                { imagePropertyChangedListener?.invoke() },
            )

        preferences.imageScaleType()
            .register({ imageScaleType = it }, { imagePropertyChangedListener?.invoke() })

        preferences.zoomStart()
            .register({ zoomTypeFromPreference(it) }, { imagePropertyChangedListener?.invoke() })

        preferences.cropBorders()
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        preferences.navigateToPan()
            .register({ navigateToPan = it })

        preferences.landscapeZoom()
            .register({ landscapeZoom = it }, { imagePropertyChangedListener?.invoke() })

        preferences.navigationModePager()
            .register({ navigationMode = it }, { updateNavigation(navigationMode) })

        preferences.pagerNavInverted()
            .register({ tappingInverted = it }, { navigator.invertMode = it })
        preferences.pagerNavInverted().asFlow()
            .drop(1)
            .onEach { navigationModeChangedListener?.invoke() }
            .launchIn(scope)

        preferences.dualPageSplitPaged()
            .register(
                { dualPageSplit = it },
                {
                    imagePropertyChangedListener?.invoke()
                    dualPageSplitChangedListener?.invoke(it)
                },
            )

        preferences.dualPageInvertPaged()
            .register({ dualPageInvert = it }, { imagePropertyChangedListener?.invoke() })

        // SY -->
        preferences.pageTransitionsPager()
            .register({ usePageTransitions = it }, { imagePropertyChangedListener?.invoke() })
        preferences.readerTheme()
            .register(
                {
                    themeToColor(it)
                },
                {
                    themeToColor(it)
                    reloadChapterListener?.invoke(doublePages)
                },
            )
        preferences.pageLayout()
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

        preferences.centerMarginType()
            .register({ centerMarginType = it }, { imagePropertyChangedListener?.invoke() })

        preferences.invertDoublePages()
            .register({ invertDoublePages = it && dualPageSplit == false }, { imagePropertyChangedListener?.invoke() })
        // SY <--
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
