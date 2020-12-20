package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Configuration used by webtoon viewers.
 */
class WebtoonConfig(preferences: PreferencesHelper = Injekt.get()) : ViewerConfig(preferences) {

    var imageCropBorders = false
        private set

    var sidePadding = 0
        private set

    // SY -->
    var enableZoomOut = false
        private set

    var continuesCropBorders = false
        private set

    var zoomPropertyChangedListener: ((Boolean) -> Unit)? = null

    // SY <--
    init {
        preferences.cropBordersWebtoon()
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        preferences.webtoonSidePadding()
            .register({ sidePadding = it }, { imagePropertyChangedListener?.invoke() })

        // SY -->
        preferences.webtoonEnableZoomOut()
            .register({ enableZoomOut = it }, { zoomPropertyChangedListener?.invoke(it) })

        preferences.cropBordersContinuesVertical()
            .register({ continuesCropBorders = it }, { imagePropertyChangedListener?.invoke() })
        // SY <--
    }
}
