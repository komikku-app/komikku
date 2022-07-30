package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R

@Composable
fun StartReadingButton(
    modifier: Modifier = Modifier,
    onOpenReader: () -> Unit,
) {
    Box(
        modifier then Modifier
            .size(50.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xAD212121))
                .clickable(onClick = onOpenReader),
        )
        Icon(
            painter = painterResource(R.drawable.ic_start_reading_24dp),
            contentDescription = stringResource(R.string.action_start_reading),
            tint = Color.White,
            modifier = Modifier
                .size(24.dp)
                .padding(3.dp),
        )
    }
}

@Preview
@Composable
fun StartReadingPreview() {
    Surface {
        StartReadingButton {}
    }
}
