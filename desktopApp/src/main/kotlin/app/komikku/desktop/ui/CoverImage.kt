package app.komikku.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.komikku.shared.reader.image.UnsupportedPageFormatException
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * KMK: a manga cover through Coil. A cover in a format the desktop cannot decode (AVIF / JPEG XL / HEIF, see
 * `UnsupportedImageFormatDecoder`) shows the format's name instead of staying blank, so it does not look like a
 * missing or broken cover; other failures keep the plain background like before.
 */
@Composable
fun CoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    imageLoader: ImageLoader = SingletonImageLoader.get(LocalPlatformContext.current),
) {
    var unsupported by remember(model) { mutableStateOf<String?>(null) }
    Box(modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            imageLoader = imageLoader,
            contentScale = contentScale,
            onState = { state ->
                unsupported = ((state as? AsyncImagePainter.State.Error)?.result?.throwable as? UnsupportedPageFormatException)?.format
            },
            modifier = Modifier.matchParentSize(),
        )
        unsupported?.let { format ->
            val description = stringResource(KMR.strings.desktop_unsupported_image_format, format)
            Text(
                text = format,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(2.dp)
                    .testTag("cover-unsupported-format")
                    .semantics { this.contentDescription = description },
            )
        }
    }
}
