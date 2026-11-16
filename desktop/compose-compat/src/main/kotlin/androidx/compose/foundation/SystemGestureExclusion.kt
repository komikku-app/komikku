package androidx.compose.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates

/**
 * compose-compat: `Modifier.systemGestureExclusion` (Android-only in foundation). Desktop has no
 * system gestures, so both overloads return the modifier unchanged.
 */
fun Modifier.systemGestureExclusion(): Modifier = this

@Suppress("UNUSED_PARAMETER")
fun Modifier.systemGestureExclusion(exclusion: (LayoutCoordinates) -> Rect): Modifier = this
