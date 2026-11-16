package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable

/**
 * compose-compat: `WindowInsets.isImeVisible` (Android-only in foundation-layout). Desktop has no
 * soft keyboard, so it is never visible.
 */
@Suppress("UnusedReceiverParameter")
val WindowInsets.Companion.isImeVisible: Boolean
    @Composable get() = false
