package eu.kanade.presentation.browse.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton

@Composable
fun BrowseSourceFloatingActionButton(
    isVisible: Boolean,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        modifier = modifier,
        text = {
            Text(
                text = if (isVisible) {
                    stringResource(R.string.action_filter)
                } else {
                    stringResource(R.string.saved_searches)
                },
            )
        },
        icon = { Icon(Icons.Outlined.FilterList, contentDescription = "") },
        onClick = onFabClick,
    )
}
