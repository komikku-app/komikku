package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

internal class CustomColorScheme(
    seed: Color,
    style: PaletteStyle,
) : BaseColorScheme() {
    override val lightScheme = generateColorSchemeFromSeed(seed = seed, style = style, dark = false)
    override val darkScheme = generateColorSchemeFromSeed(seed = seed, style = style, dark = true)

    companion object {
        fun generateColorSchemeFromSeed(seed: Color, style: PaletteStyle, dark: Boolean): ColorScheme {
            return dynamicColorScheme(
                seedColor = seed,
                isDark = dark,
                isAmoled = false,
                style = style,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
            )
        }
    }
}
