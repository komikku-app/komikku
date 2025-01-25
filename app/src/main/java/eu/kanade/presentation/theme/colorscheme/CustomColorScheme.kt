package eu.kanade.presentation.theme.colorscheme

import android.app.UiModeManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

internal class CustomColorScheme(
    context: Context,
    seed: Int,
    style: PaletteStyle,
) : BaseColorScheme() {
    private val custom = CustomCompatColorScheme(context, seed, style)

    override val darkScheme
        get() = custom.darkScheme

    override val lightScheme
        get() = custom.lightScheme
}

private class CustomCompatColorScheme(
    context: Context,
    seed: Int,
    style: PaletteStyle,
) : BaseColorScheme() {
    override val lightScheme = dynamicColorScheme(
        seedColor = Color(seed),
        isDark = false,
        isAmoled = false,
        style = style,
        contrastLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService<UiModeManager>()?.contrast?.toDouble() ?: Contrast.Default.value
        } else {
            Contrast.Default.value
        },
    )
    override val darkScheme = dynamicColorScheme(
        seedColor = Color(seed),
        isDark = true,
        isAmoled = false,
        style = style,
        contrastLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService<UiModeManager>()?.contrast?.toDouble() ?: Contrast.Default.value
        } else {
            Contrast.Default.value
        },
    )
}

class AndroidViewColorScheme(
    colorScheme: ColorScheme,
) {
    @ColorInt
    val textColor: Int = colorScheme.onSurfaceVariant.toArgb()

    @ColorInt
    val textHighlightColor: Int = colorScheme.inversePrimary.toArgb()

    @ColorInt
    val iconColor: Int = colorScheme.primary.toArgb()

    @ColorInt
    val tagColor: Int = colorScheme.outlineVariant.toArgb()

    @ColorInt
    val tagTextColor: Int = colorScheme.onSurfaceVariant.toArgb()

    @ColorInt
    val btnTextColor: Int = colorScheme.onPrimary.toArgb()

    @ColorInt
    val btnBgColor: Int = colorScheme.surfaceTint.toArgb()

    @ColorInt
    val dropdownBgColor: Int = colorScheme.surfaceContainerHighest.toArgb()

    @ColorInt
    val dialogBgColor: Int = colorScheme.surfaceContainerHigh.toArgb()

    @ColorInt
    val primary = colorScheme.primary.toArgb()

    @ColorInt
    val onPrimary = colorScheme.onPrimary.toArgb()

    @ColorInt
    val surface = colorScheme.surface.toArgb()

    @ColorInt
    val onSurface = colorScheme.onSurface.toArgb()

    @ColorInt
    val secondary = colorScheme.secondary.toArgb()

    @ColorInt
    val surfaceElevation = colorScheme.surfaceColorAtElevation(4.dp).toArgb()

    /* MaterialSwitch */
    val trackTintList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        ),
        intArrayOf(
            primary,
            surface,
        ),
    )
    val thumbTintList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        ),
        intArrayOf(
            onPrimary,
            onSurface,
        ),
    )

    val checkboxTintList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        ),
        intArrayOf(
            primary,
            onSurface,
        ),
    )

    val editTextBackgroundTintList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(-android.R.attr.state_focused),
        ),
        intArrayOf(
            primary,
            onSurface,
        ),
    )

    val imageButtonTintList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_pressed), // Pressed state
            intArrayOf(android.R.attr.state_focused), // Focused state
            intArrayOf(), // Default state
        ),
        intArrayOf(
            primary, // Pressed color
            primary, // Focused color
            primary, // Default color
        ),
    )
}
