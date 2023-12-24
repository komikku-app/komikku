package eu.kanade.tachiyomi.ui.browse.source.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource

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
            Text(stringResource(SYMR.strings.random))
        }
        TextButton(onClick = openMangaDexFollows) {
            Text(stringResource(SYMR.strings.mangadex_follows))
        }
    }
}
