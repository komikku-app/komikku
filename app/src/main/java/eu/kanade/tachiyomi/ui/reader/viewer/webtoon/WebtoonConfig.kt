package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Configuration used by webtoon viewers.
 */
class WebtoonConfig(
    scope: CoroutineScope,
    readerPreferences: ReaderPreferences = Injekt.get(),
) : ViewerConfig(readerPreferences, scope) {

    var themeChangedListener: (() -> Unit)? = null

    var imageCropBorders = false
        private set

    var zoomOutDisabled = false
        private set

    var zoomPropertyChangedListener: ((Boolean) -> Unit)? = null

    var sidePadding = 0
        private set

    var doubleTapZoom = true
        private set

    var doubleTapZoomChangedListener: ((Boolean) -> Unit)? = null

    // KMK -->
    var pinchToZoom = true
        private set

    var pinchToZoomChangedListener: ((Boolean) -> Unit)? = null

    var webtoonScaleType = readerPreferences.webtoonScaleType().get()
        private set

    var webtoonScaleTypeChangedListener: ((ReaderPreferences.WebtoonScaleType) -> Unit)? = null
    // KMK <--

    // SY -->
    var usePageTransitions = false

    var continuousCropBorders = false
        private set

    // SY <--
    init {
        readerPreferences.cropBordersWebtoon()
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.webtoonSidePadding()
            .register({ sidePadding = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.navigationModeWebtoon()
            .register({ navigationMode = it }, { updateNavigation(it) })

        readerPreferences.webtoonNavInverted()
            .register({ tappingInverted = it }, { navigator.invertMode = it })
        readerPreferences.webtoonNavInverted().changes()
            .drop(1)
            .onEach { navigationModeChangedListener?.invoke() }
            .launchIn(scope)
        // KMK -->
        readerPreferences.smallerTapZone().changes()
            .drop(1)
            .onEach { updateNavigation(navigationMode) }
            .launchIn(scope)
        // KMK <--

        readerPreferences.dualPageSplitWebtoon()
            .register({ dualPageSplit = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageInvertWebtoon()
            .register({ dualPageInvert = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.dualPageRotateToFitWebtoon()
            .register(
                { dualPageRotateToFit = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.dualPageRotateToFitInvertWebtoon()
            .register(
                { dualPageRotateToFitInvert = it },
                { imagePropertyChangedListener?.invoke() },
            )

        readerPreferences.webtoonDisableZoomOut()
            .register(
                { zoomOutDisabled = it },
                { zoomPropertyChangedListener?.invoke(it) },
            )

        readerPreferences.webtoonDoubleTapZoomEnabled()
            .register(
                { doubleTapZoom = it },
                { doubleTapZoomChangedListener?.invoke(it) },
            )

        // KMK -->
        readerPreferences.webtoonPinchToZoomEnabled()
            .register(
                { pinchToZoom = it },
                { pinchToZoomChangedListener?.invoke(it) },
            )

        readerPreferences.webtoonScaleType()
            .register(
                { webtoonScaleType = it },
                { webtoonScaleTypeChangedListener?.invoke(it) },
            )
        // KMK <--

        readerPreferences.readerTheme().changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach { themeChangedListener?.invoke() }
            .launchIn(scope)

        // SY -->
        readerPreferences.cropBordersContinuousVertical()
            .register({ continuousCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        readerPreferences.pageTransitionsWebtoon()
            .register({ usePageTransitions = it }, { imagePropertyChangedListener?.invoke() })
        // SY <--

        // KMK --> Image enhancement (upscale) - refresh pages when changed
        readerPreferences.realCuganEnabled().changes()
            .drop(1)
            .onEach {
                ImageEnhancer.cancelAll("realCuganEnabled changed")
                ImageEnhancementCache.clear(appContext)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganModel().changes()
            .drop(1)
            .onEach {
                ImageEnhancer.cancelAll("realCuganModel changed")
                ImageEnhancementCache.clear(appContext)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realEsrganStyle().changes()
            .drop(1)
            .onEach {
                ImageEnhancer.cancelAll("realEsrganStyle changed")
                ImageEnhancementCache.clear(appContext)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganNoiseLevel().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganNoiseLevel changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(appContext)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganScale().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganScale changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(appContext)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganMaxSizeWidth().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganMaxSizeWidth changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(appContext)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganMaxSizeHeight().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganMaxSizeHeight changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(appContext)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganSkipMaxSizeWidth().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganSkipMaxSizeWidth changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(appContext)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganSkipMaxSizeHeight().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganSkipMaxSizeHeight changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(appContext)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganTileSize().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganTileSize changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(appContext)
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        readerPreferences.realCuganPrecision().changes()
            .drop(1)
            .onEach { ImageEnhancer.cancelAll("realCuganPrecision changed") }
            .debounce(500)
            .onEach {
                ImageEnhancementCache.clear(appContext)
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
                ImageEnhancementCache.clear(appContext)
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

    // KMK -->
    private val appContext = Injekt.get<Application>()
    // KMK <--

    override var navigator: ViewerNavigation = defaultNavigation()
        set(value) {
            field = value.also { it.invertMode = tappingInverted }
        }

    override fun defaultNavigation(): ViewerNavigation {
        return LNavigation()
    }

    override fun updateNavigation(navigationMode: Int) {
        this.navigator = when (navigationMode) {
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
}
