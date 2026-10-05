package tachiyomi.presentation.core.theme

import androidx.compose.material3.Typography

/**
 * The type scale of the app.
 *
 * Material 3 Expressive gives every role an emphasized style beside the standard one, for example
 * `titleLargeEmphasized` beside `titleLarge`. Both are members of [Typography], so a screen reads
 * the emphasized style from `MaterialTheme.typography`.
 *
 * The library still carries a TODO for the default values of the emphasized styles, so they
 * currently measure as their standard counterparts. A call site needs no change when the library
 * fills the values in.
 */
val AppTypography: Typography = Typography()
