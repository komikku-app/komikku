package eu.kanade.presentation.util

import android.content.res.Resources
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Create a BitmapPainter from a drawable resource.
 * Use this only if [androidx.compose.ui.res.painterResource] doesn't work.
 *
 * @param id the resource identifier
 *
 * @return the bitmap associated with the resource
 */
@Composable
fun rememberResourceBitmapPainter(
    @DrawableRes id: Int,
    // KMK -->
    @ColorInt tint: Int? = null,
    // KMK <--
): BitmapPainter {
    val context = LocalContext.current
    return remember(id) {
        val drawable = ContextCompat.getDrawable(context, id)
            ?: throw Resources.NotFoundException()
        // KMK -->
        tint?.let { drawable.setTint(it) }
        // KMK <--
        BitmapPainter(drawable.toBitmap().asImageBitmap())
    }
}
