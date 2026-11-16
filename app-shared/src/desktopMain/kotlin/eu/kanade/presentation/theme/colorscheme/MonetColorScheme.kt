package eu.kanade.presentation.theme.colorscheme

import android.content.Context

/**
 * KMK: desktop counterpart of the app's `MonetColorScheme` (Android wallpaper / system dynamic
 * colors, borrowed for Android only). Desktop has no wallpaper color API, so the "Dynamic" theme
 * uses the default Tachiyomi colors.
 */
internal class MonetColorScheme(
    @Suppress("UNUSED_PARAMETER") context: Context,
) : BaseColorScheme() {

    override val darkScheme
        get() = TachiyomiColorScheme.darkScheme

    override val lightScheme
        get() = TachiyomiColorScheme.lightScheme
}
