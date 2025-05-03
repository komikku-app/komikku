package eu.kanade.presentation.theme.colorscheme

import android.app.UiModeManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.widget.EditText
import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
    @ColorInt val primary: Int = colorScheme.primary.toArgb()

    @ColorInt val onPrimary: Int = colorScheme.onPrimary.toArgb()

    @ColorInt val primaryContainer: Int = colorScheme.primaryContainer.toArgb()

    @ColorInt val onPrimaryContainer: Int = colorScheme.onPrimaryContainer.toArgb()

    @ColorInt val inversePrimary: Int = colorScheme.inversePrimary.toArgb()

    @ColorInt val secondary: Int = colorScheme.secondary.toArgb()

    @ColorInt val onSecondary: Int = colorScheme.onSecondary.toArgb()

    @ColorInt val secondaryContainer: Int = colorScheme.secondaryContainer.toArgb()

    @ColorInt val onSecondaryContainer: Int = colorScheme.onSecondaryContainer.toArgb()

    @ColorInt val tertiary: Int = colorScheme.tertiary.toArgb()

    @ColorInt val onTertiary: Int = colorScheme.onTertiary.toArgb()

    @ColorInt val tertiaryContainer: Int = colorScheme.tertiaryContainer.toArgb()

    @ColorInt val onTertiaryContainer: Int = colorScheme.onTertiaryContainer.toArgb()

    @ColorInt val background: Int = colorScheme.background.toArgb()

    @ColorInt val onBackground: Int = colorScheme.onBackground.toArgb()

    @ColorInt val surface: Int = colorScheme.surface.toArgb()

    @ColorInt val onSurface: Int = colorScheme.onSurface.toArgb()

    @ColorInt val surfaceVariant: Int = colorScheme.surfaceVariant.toArgb()

    @ColorInt val onSurfaceVariant: Int = colorScheme.onSurfaceVariant.toArgb()

    @ColorInt val surfaceTint: Int = colorScheme.surfaceTint.toArgb()

    @ColorInt val inverseSurface: Int = colorScheme.inverseSurface.toArgb()

    @ColorInt val inverseOnSurface: Int = colorScheme.inverseOnSurface.toArgb()

    @ColorInt val error: Int = colorScheme.error.toArgb()

    @ColorInt val onError: Int = colorScheme.onError.toArgb()

    @ColorInt val errorContainer: Int = colorScheme.errorContainer.toArgb()

    @ColorInt val onErrorContainer: Int = colorScheme.onErrorContainer.toArgb()

    @ColorInt val outline: Int = colorScheme.outline.toArgb()

    @ColorInt val outlineVariant: Int = colorScheme.outlineVariant.toArgb()

    @ColorInt val scrim: Int = colorScheme.scrim.toArgb()

    @ColorInt val surfaceBright: Int = colorScheme.surfaceBright.toArgb()

    @ColorInt val surfaceDim: Int = colorScheme.surfaceDim.toArgb()

    @ColorInt val surfaceContainer: Int = colorScheme.surfaceContainer.toArgb()

    @ColorInt val surfaceContainerHigh: Int = colorScheme.surfaceContainerHigh.toArgb()

    @ColorInt val surfaceContainerHighest: Int = colorScheme.surfaceContainerHighest.toArgb()

    @ColorInt val surfaceContainerLow: Int = colorScheme.surfaceContainerLow.toArgb()

    @ColorInt val surfaceContainerLowest: Int = colorScheme.surfaceContainerLowest.toArgb()

    @ColorInt
    val textColor: Int = onSurfaceVariant

    @ColorInt
    val textHighlightColor: Int = inversePrimary

    @ColorInt
    val iconColor: Int = primary

    @ColorInt
    val tagColor: Int = outlineVariant

    @ColorInt
    val tagTextColor: Int = onSurfaceVariant

    @ColorInt
    val btnTextColor: Int = onPrimary

    @ColorInt
    val btnBgColor: Int = surfaceTint

    @ColorInt
    val dropdownBgColor: Int = surfaceContainerHighest

    @ColorInt
    val dialogBgColor: Int = surfaceContainerHigh

    @ColorInt
    val surfaceElevation = colorScheme.surfaceColorAtElevation(4.dp).toArgb()

    @ColorInt
    val ratingBarColor = primary

    @ColorInt
    val ratingBarSecondaryColor = outlineVariant

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
            onSurfaceVariant,
        ),
    )

    val checkboxTintList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        ),
        intArrayOf(
            primary,
            onSurfaceVariant,
        ),
    )

    val editTextBackgroundTintList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(-android.R.attr.state_focused),
        ),
        intArrayOf(
            primary,
            onSurfaceVariant,
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

    /**
     * Set the color of the [TextInputEditText] or [EditText].
     * @param editText The EditText to set the color for.
     */
    fun setEditTextColor(
        editText: TextInputEditText,
    ) {
        editText.setTextColor(onSurfaceVariant)
        editText.highlightColor = inversePrimary
        editText.backgroundTintList = editTextBackgroundTintList

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.textSelectHandle?.let { drawable ->
                drawable.setTint(primary)
                editText.setTextSelectHandle(drawable)
            }
            editText.textSelectHandleLeft?.let { drawable ->
                drawable.setTint(primary)
                editText.setTextSelectHandleLeft(drawable)
            }
            editText.textSelectHandleRight?.let { drawable ->
                drawable.setTint(primary)
                editText.setTextSelectHandleRight(drawable)
            }
        }
    }

    fun setTextInputLayoutColor(
        inputLayout: TextInputLayout,
        isError: Boolean = false,
    ) {
        val (strokeColorFocused, strokeColorDefault, hintColor, cursorColor) = if (isError) {
            arrayOf(error, error, error, error)
        } else {
            arrayOf(primary, onSurfaceVariant, primary, primary)
        }

        val boxStrokeColorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(), // Default state
            ),
            intArrayOf(
                strokeColorFocused,
                strokeColorDefault,
            ),
        )
        val hintTextColorStateList = ColorStateList.valueOf(hintColor)
        val endIconTintList = ColorStateList.valueOf(onSurfaceVariant)
        val cursorColorStateList = ColorStateList.valueOf(cursorColor)

        inputLayout.setBoxStrokeColorStateList(boxStrokeColorStateList)
        inputLayout.hintTextColor = hintTextColorStateList
        inputLayout.setEndIconTintList(endIconTintList)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            inputLayout.cursorColor = cursorColorStateList
        }
    }

    companion object {
        fun LinearProgressIndicator.setColors(colorScheme: AndroidViewColorScheme) {
            trackColor = colorScheme.secondaryContainer
            setIndicatorColor(colorScheme.primary)
        }
    }
}
