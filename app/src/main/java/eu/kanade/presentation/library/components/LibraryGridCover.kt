package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.BadgeGroup
import eu.kanade.presentation.components.MangaCover
import eu.kanade.tachiyomi.R

@Composable
fun LibraryGridCover(
    modifier: Modifier = Modifier,
    mangaCover: eu.kanade.domain.manga.model.MangaCover,
    downloadCount: Int,
    unreadCount: Int,
    isLocal: Boolean,
    language: String,
    // SY -->
    showPlayButton: Boolean,
    playButtonPosition: PlayButtonPosition = PlayButtonPosition.Bottom,
    onOpenReader: () -> Unit,
    // SY <--
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(MangaCover.Book.ratio),
    ) {
        MangaCover.Book(
            modifier = Modifier.fillMaxWidth(),
            data = mangaCover,
        )
        content()
        if (downloadCount > 0 || unreadCount > 0) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
            ) {
                if (downloadCount > 0) {
                    Badge(
                        text = "$downloadCount",
                        color = MaterialTheme.colorScheme.tertiary,
                        textColor = MaterialTheme.colorScheme.onTertiary,
                    )
                }
                if (unreadCount > 0) {
                    Badge(text = "$unreadCount")
                }
            }
        }
        if (isLocal || language.isNotEmpty()) {
            // SY -->
            Column(
                Modifier.align(Alignment.TopEnd),
                horizontalAlignment = Alignment.End,
            ) {
                // SY <--
                BadgeGroup(
                    modifier = Modifier
                        .padding(4.dp),
                ) {
                    if (isLocal) {
                        Badge(
                            text = stringResource(R.string.local_source_badge),
                            color = MaterialTheme.colorScheme.tertiary,
                            textColor = MaterialTheme.colorScheme.onTertiary,
                        )
                    } else if (language.isNotEmpty()) {
                        Badge(
                            text = language,
                            color = MaterialTheme.colorScheme.tertiary,
                            textColor = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                }
                // SY -->
                if (showPlayButton && playButtonPosition == PlayButtonPosition.Top) {
                    StartReadingButton(onOpenReader = onOpenReader)
                }
            }
            if (showPlayButton && playButtonPosition == PlayButtonPosition.Bottom) {
                StartReadingButton(
                    Modifier.align(playButtonPosition.alignment),
                    onOpenReader = onOpenReader,
                )
            }
        } else if (showPlayButton) {
            StartReadingButton(
                modifier = Modifier.align(playButtonPosition.alignment),
                onOpenReader = onOpenReader,
            )
        }
        // SY <--
    }
}

enum class PlayButtonPosition(val alignment: Alignment) {
    Top(Alignment.TopEnd),
    Bottom(Alignment.BottomEnd)
}
