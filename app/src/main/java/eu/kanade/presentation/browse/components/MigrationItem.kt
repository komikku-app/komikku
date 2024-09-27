package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.manga.components.CoverPlaceholderColor
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.RatioSwitchToPanorama
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MigrationItem(
    modifier: Modifier,
    manga: Manga,
    sourcesString: String,
    chapterInfo: MigratingManga.ChapterInfo,
    onClick: () -> Unit,
    // KMK -->
    coverRatio: MutableFloatState = remember { mutableFloatStateOf(1f) },
    // KMK <--
) {
    // KMK -->
    val usePanoramaCover by Injekt.get<UiPreferences>().usePanoramaCover().collectAsState()
    val coverIsWide = coverRatio.floatValue <= RatioSwitchToPanorama
    val bgColor = manga.asMangaCover().dominantCoverColors?.first?.let { Color(it) }
    // KMK <--
    Column(
        modifier
            .widthIn(max = 150.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        val context = LocalContext.current
        Box(
            Modifier.fillMaxWidth()
                // KMK -->
                .background(bgColor ?: CoverPlaceholderColor)
                // KMK <--
                .aspectRatio(MangaCover.Book.ratio),
        ) {
            // KMK -->
            if (usePanoramaCover && coverIsWide) {
                MangaCover.Panorama(
                    modifier = Modifier
                        // KMK -->
                        .align(Alignment.Center)
                        // KMK <--
                        .fillMaxWidth(),
                    data = manga,
                    // KMK -->
                    onCoverLoaded = { _, result ->
                        val image = result.result.image
                        coverRatio.floatValue = image.height.toFloat() / image.width
                    },
                    // KMK <--
                )
            } else {
                // KMK <--
                MangaCover.Book(
                    modifier = Modifier
                        .fillMaxWidth(),
                    data = manga,
                    // KMK -->
                    onCoverLoaded = { _, result ->
                        val image = result.result.image
                        coverRatio.floatValue = image.height.toFloat() / image.width
                    },
                    // KMK <--
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0xAA000000),
                        ),
                    )
                    .fillMaxHeight(0.33f)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
            Text(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart),
                text = manga.title.ifBlank { stringResource(MR.strings.unknown) },
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color.White,
                    shadow = Shadow(
                        color = Color.Black,
                        blurRadius = 4f,
                    ),
                ),
            )
            BadgeGroup(modifier = Modifier.padding(4.dp)) {
                Badge(text = "${chapterInfo.chapterCount}")
            }
        }
        Text(
            text = sourcesString,
            modifier = Modifier.padding(top = 4.dp, bottom = 1.dp, start = 8.dp),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
        )

        val formattedLatestChapter by produceState(initialValue = "") {
            value = withIOContext {
                chapterInfo.getFormattedLatestChapter(context)
            }
        }
        Text(
            text = formattedLatestChapter,
            modifier = Modifier.padding(top = 1.dp, bottom = 4.dp, start = 8.dp),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
