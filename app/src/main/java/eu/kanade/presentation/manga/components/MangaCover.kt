package eu.kanade.presentation.manga.components

import androidx.annotation.ColorInt
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.delay
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.manga.model.MangaCover as DomainMangaCover

enum class MangaCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),
    ;

    @Composable
    operator fun invoke(
        data: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        shape: Shape = MaterialTheme.shapes.extraSmall,
        onClick: (() -> Unit)? = null,
        alpha: Float = 1f,
        bgColor: Color? = null,
        @ColorInt tint: Int? = null,
        onCoverLoaded: ((DomainMangaCover) -> Unit)? = null,
    ) {
        val animatedImageVector = AnimatedImageVector.animatedVectorResource(R.drawable.anim_waiting)
        var atEnd by remember { mutableStateOf(false) }

        suspend fun runAnimation() {
            while (true) {
                delay(LoadingAnimatedDuration)
                atEnd = !atEnd
            }
        }

        LaunchedEffect(animatedImageVector) {
            runAnimation()
        }

        var succeed by remember { mutableStateOf(false) }

        AsyncImage(
            model = data,
            placeholder = rememberAnimatedVectorPainter(animatedImageVector = animatedImageVector, atEnd = atEnd),
            error = rememberResourceBitmapPainter(id = R.drawable.cover_error, tint),
            fallback = rememberResourceBitmapPainter(id = R.drawable.cover_error, tint),
            onSuccess = {
                succeed = true
                if (onCoverLoaded != null) {
                    when (data) {
                        is Manga -> onCoverLoaded(data.asMangaCover())
                        is DomainMangaCover -> onCoverLoaded(data)
                    }
                }
            },
            contentDescription = contentDescription,
            modifier = modifier
                .aspectRatio(ratio)
                .clip(shape)
                .alpha(if (succeed) alpha else 1f)
                .background(bgColor ?: Color.Transparent)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentScale = ContentScale.Crop,
        )
    }
}

const val LoadingAnimatedDuration = 600L
