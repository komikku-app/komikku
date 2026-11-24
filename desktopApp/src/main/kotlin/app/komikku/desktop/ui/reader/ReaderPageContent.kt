package app.komikku.desktop.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.komikku.shared.reader.image.AnimationFrame
import app.komikku.shared.reader.image.AnimationFrameCache
import app.komikku.shared.reader.image.DecodeRequest
import app.komikku.shared.reader.image.DecodedPage
import app.komikku.shared.reader.image.DecodedPageCache
import app.komikku.shared.reader.image.SkiaPageDecoder
import app.komikku.shared.reader.image.UnsupportedPageFormatException
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

/** Decoder + decoded-page LRU (+ the frames of shown animated pages) shared by all reader screens of the process. */
object ReaderImages {
    val decoder = SkiaPageDecoder()
    val cache = DecodedPageCache()
    val animations = AnimationFrameCache()

    fun key(page: ReaderPage, maxWidth: Int) =
        DecodedPageCache.Key("${page.chapter.chapter.id}-${page.index}", maxWidth)
}

/** Result of decoding a page for display. */
sealed interface PageImageState {
    data object Loading : PageImageState
    data class Ready(val page: DecodedPage<ImageBitmap>) : PageImageState
    data class Failed(val error: Throwable) : PageImageState
}

/**
 * Starts loading [page] through its chapter's page loader while composed (the loader's `loadPage`
 * suspends until cancelled, like on Android), and decodes it once ready at [maxWidth] (bucketed so
 * small window resizes reuse the cache).
 */
@Composable
fun rememberPageImage(page: ReaderPage, maxWidth: Int): PageImageState {
    LaunchedEffect(page) {
        page.chapter.pageLoader?.loadPage(page)
    }
    val status by page.statusFlow.collectAsState()
    val bucket = decodeBucket(maxWidth)
    val decoded by produceState<PageImageState>(PageImageState.Loading, page, status, bucket) {
        if (status != Page.State.Ready) {
            value = if (status is Page.State.Error) PageImageState.Failed((status as Page.State.Error).error) else PageImageState.Loading
            return@produceState
        }
        val key = ReaderImages.key(page, bucket)
        ReaderImages.cache[key]?.let {
            value = PageImageState.Ready(it)
            return@produceState
        }
        val stream = page.stream ?: return@produceState
        value = try {
            val result = ReaderImages.decoder.decode(stream, DecodeRequest(maxWidth = bucket))
            ReaderImages.cache.put(key, result)
            PageImageState.Ready(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            PageImageState.Failed(e)
        }
    }
    return decoded
}

/** The decode width for a slot [maxWidth] pixels wide, in steps of 256 so that small window resizes reuse the cache. */
fun decodeBucket(maxWidth: Int): Int = ((maxWidth.coerceAtLeast(1) + 255) / 256) * 256

/**
 * The frames of the animated [page] decoded as [decoded] for a slot [maxWidth] wide (P11 review fixes): decoded only
 * while [shown] (never for preloaded pages), after the first frame is already on screen, on the decoder's animation
 * slot, cancelled when the page leaves the screen; kept in [ReaderImages.animations] (one frame set per page). Empty
 * until then, for still pages and when the frames do not fit (the first frame stays).
 */
@Composable
fun rememberAnimationFrames(
    page: ReaderPage,
    decoded: DecodedPage<ImageBitmap>,
    maxWidth: Int,
    shown: Boolean,
): List<AnimationFrame<ImageBitmap>> {
    val bucket = decodeBucket(maxWidth)
    val frames by produceState(emptyList<AnimationFrame<ImageBitmap>>(), page, decoded, bucket, shown) {
        if (!shown || !decoded.animated) {
            value = emptyList()
            return@produceState
        }
        val key = ReaderImages.key(page, bucket).pageKey
        ReaderImages.animations[key, bucket]?.let {
            value = it
            return@produceState
        }
        val stream = page.stream ?: return@produceState
        val result = ReaderImages.decoder.decodeFrames(stream, decoded, DecodeRequest(maxWidth = bucket))
        if (result.size > 1) {
            ReaderImages.animations.put(key, bucket, result, decoded.decoded)
            value = result
        }
    }
    return frames
}

/**
 * Decoded strips stacked vertically, each filling the width; with [frames] (an animated page that is shown,
 * [rememberAnimationFrames]) the frames play instead (P11, Android plays animated GIF / WebP pages too), looping,
 * while composed.
 */
@Composable
fun PageTiles(
    page: DecodedPage<ImageBitmap>,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier,
    frames: List<AnimationFrame<ImageBitmap>> = emptyList(),
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Top) {
        if (frames.size > 1) {
            AnimatedFrames(frames, colorFilter)
            return@Column
        }
        page.tiles.forEachIndexed { i, tile ->
            Image(
                bitmap = tile,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxWidth().testTag("page-tile-$i"),
            )
        }
    }
}

@Composable
private fun AnimatedFrames(frames: List<AnimationFrame<ImageBitmap>>, colorFilter: ColorFilter?) {
    var index by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(frames) {
        while (true) {
            delay(frames[index].durationMillis.toLong())
            index = (index + 1) % frames.size
        }
    }
    Image(
        bitmap = frames[index].image,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        colorFilter = colorFilter,
        modifier = Modifier.fillMaxWidth().testTag("page-frame-$index"),
    )
}

/** Loading indicator (with download progress) / error with retry for a page that has no image yet. */
@Composable
fun PagePlaceholder(page: ReaderPage, state: PageImageState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val progress by page.progressFlow.collectAsState()
    Box(modifier = modifier.testTag("page-placeholder-${page.index}"), contentAlignment = Alignment.Center) {
        when (state) {
            is PageImageState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.BrokenImage, contentDescription = null, modifier = Modifier.size(48.dp))
                val message = when (val error = state.error) {
                    is UnsupportedPageFormatException -> stringResource(KMR.strings.desktop_unsupported_image_format, error.format)
                    else -> stringResource(MR.strings.decode_image_error) + (error.message?.let { "\n$it" } ?: "")
                }
                Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                if (state.error !is UnsupportedPageFormatException) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("page-retry")) {
                        Text(stringResource(MR.strings.action_retry))
                    }
                }
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    text = page.number.toString() + if (progress > 0) " · $progress%" else "",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Full-size page slot: the image when decoded, else the placeholder. */
@Composable
fun ReaderPageSlot(
    page: ReaderPage,
    maxWidth: Int,
    colorFilter: ColorFilter?,
    onRetry: () -> Unit,
    content: @Composable (DecodedPage<ImageBitmap>) -> Unit,
) {
    when (val state = rememberPageImage(page, maxWidth)) {
        is PageImageState.Ready -> content(state.page)
        else -> PagePlaceholder(page, state, onRetry, Modifier.fillMaxSize())
    }
}
