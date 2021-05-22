package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Color
import androidx.annotation.ColorInt
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
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
    preferences: PreferencesHelper = Injekt.get()
) : ViewerConfig(preferences, scope) {

    var automaticBackground = false
        private set

    var dualPageSplitChangedListener: ((Boolean) -> Unit)? = null

    var reloadChapterListener: ((Boolean) -> Unit)? = null

    var imageScaleType = 1
        private set

    var imageZoomType = ZoomType.Left
        private set

    var imageCropBorders = false
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
    // SY <--

    init {
        preferences.readerTheme()
            .register({ automaticBackground = it == 3 }, { imagePropertyChangedListener?.invoke() })

        preferences.imageScaleType()
            .register({ imageScaleType = it }, { imagePropertyChangedListener?.invoke() })

        preferences.zoomStart()
            .register({ zoomTypeFromPreference(it) }, { imagePropertyChangedListener?.invoke() })

        preferences.cropBorders()
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

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
                }
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
                }
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
                }
            )

        // SY <--
    }

    private fun zoomTypeFromPreference(value: Int) {
        imageZoomType = when (value) {
            // Auto
            1 -> when (viewer) {
                is L2RPagerViewer -> ZoomType.Left
                is R2LPagerViewer -> ZoomType.Right
                else -> ZoomType.Center
            }
            // Left
            2 -> ZoomType.Left
            // Right
            3 -> ZoomType.Right
            // Center
            else -> ZoomType.Center
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
            else -> defaultNavigation()
        }
        navigationModeChangedListener?.invoke()
    }

    enum class ZoomType {
        Left, Center, Right
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
