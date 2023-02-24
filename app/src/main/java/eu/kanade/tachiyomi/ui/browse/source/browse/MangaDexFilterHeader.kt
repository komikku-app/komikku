package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.material.TextButton

@Composable
fun MangaDexFilterHeader(
    openMangaDexRandom: () -> Unit,
    openMangaDexFollows: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = SettingsItemsPaddings.Horizontal),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = openMangaDexRandom) {
            Text(stringResource(R.string.random))
        }
        TextButton(onClick = openMangaDexFollows) {
            Text(stringResource(R.string.mangadex_follows))
        }
    }
}
