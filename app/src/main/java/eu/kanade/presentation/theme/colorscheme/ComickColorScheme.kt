package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Comick theme
 * Inspired by the Comick mascot dragon
 *
 * Key colors:
 * Primary #00D2D3 (Cyan)
 * Secondary #FDCB6E (Gold)
 * Tertiary #55EFC4 (Green)
 */
internal object ComickColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF00D2D3),
        onPrimary = Color(0xFF003738),
        primaryContainer = Color(0xFF004F50),
        onPrimaryContainer = Color(0xFF82F1F2),
        inversePrimary = Color(0xFF00696A),
        secondary = Color(0xFFFDCB6E),
        onSecondary = Color(0xFF422C00),
        secondaryContainer = Color(0xFF5E4100),
        onSecondaryContainer = Color(0xFFFFDEA3),
        tertiary = Color(0xFF55EFC4),
        onTertiary = Color(0xFF00382D),
        tertiaryContainer = Color(0xFF005143),
        onTertiaryContainer = Color(0xFF76FFDE),
        background = Color(0xFF191C1C),
        onBackground = Color(0xFFE1E3E3),
        surface = Color(0xFF191C1C),
        onSurface = Color(0xFFE1E3E3),
        surfaceVariant = Color(0xFF3F4949),
        onSurfaceVariant = Color(0xFFBEC8C8),
        surfaceTint = Color(0xFF00D2D3),
        inverseSurface = Color(0xFFE1E3E3),
        inverseOnSurface = Color(0xFF191C1C),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF899393),
        outlineVariant = Color(0xFF3F4949),
        surfaceContainerLowest = Color(0xFF0C0F0F),
        surfaceContainerLow = Color(0xFF171B1B),
        surfaceContainer = Color(0xFF1B1F1F),
        surfaceContainerHigh = Color(0xFF252A2A),
        surfaceContainerHighest = Color(0xFF303535),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF00696A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF82F1F2),
        onPrimaryContainer = Color(0xFF002021),
        inversePrimary = Color(0xFF00D2D3),
        secondary = Color(0xFF7A5900),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFDEA3),
        onSecondaryContainer = Color(0xFF261900),
        tertiary = Color(0xFF006B5A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF76FFDE),
        onTertiaryContainer = Color(0xFF00201A),
        background = Color(0xFFF4FBFA),
        onBackground = Color(0xFF191C1C),
        surface = Color(0xFFF4FBFA),
        onSurface = Color(0xFF191C1C),
        surfaceVariant = Color(0xFFDAE4E4),
        onSurfaceVariant = Color(0xFF3F4949),
        surfaceTint = Color(0xFF00696A),
        inverseSurface = Color(0xFF2E3131),
        inverseOnSurface = Color(0xFFEFF1F1),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF6F7979),
        outlineVariant = Color(0xFFBEC8C8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F4F4),
        surfaceContainer = Color(0xFFEBEFEF),
        surfaceContainerHigh = Color(0xFFE5E9E9),
        surfaceContainerHighest = Color(0xFFDFE3E3),
    )
}
