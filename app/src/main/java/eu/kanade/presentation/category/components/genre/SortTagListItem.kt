package eu.kanade.presentation.category.components.genre

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.util.horizontalPadding

@Composable
fun SortTagListItem(
    modifier: Modifier,
    tag: String,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = horizontalPadding, top = horizontalPadding, end = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Outlined.Label, contentDescription = "")
            Text(text = tag, modifier = Modifier.padding(start = horizontalPadding))
        }
        Row {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
            ) {
                Icon(imageVector = Icons.Outlined.ArrowDropUp, contentDescription = "")
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
            ) {
                Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = "")
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = "")
            }
        }
    }
}
