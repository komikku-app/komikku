package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CategoryListItem(
    category: Category,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: (Category) -> Unit,
    onMoveDown: (Category) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    // KMK -->
    onHide: () -> Unit,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRename() }
                .padding(
                    start = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Label,
                contentDescription = null,
                // KMK -->
                tint = LocalContentColor.current.let { if (category.hidden) it.copy(alpha = 0.6f) else it },
                // KMK <--
            )
            Text(
                text = category.name,
                // KMK -->
                color = LocalContentColor.current.let { if (category.hidden) it.copy(alpha = 0.6f) else it },
                textDecoration = TextDecoration.LineThrough.takeIf { category.hidden },
                // KMK <--
                modifier = Modifier
                    .padding(start = MaterialTheme.padding.medium),
            )
        }
        Row {
            IconButton(
                onClick = { onMoveUp(category) },
                enabled = canMoveUp,
            ) {
                Icon(imageVector = Icons.Outlined.ArrowDropUp, contentDescription = null)
            }
            IconButton(
                onClick = { onMoveDown(category) },
                enabled = canMoveDown,
            ) {
                Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(MR.strings.action_rename_category),
                )
            }
            // KMK -->
            IconButton(
                onClick = onHide,
                content = {
                    Icon(
                        imageVector = if (category.hidden) {
                            Icons.Outlined.Visibility
                        } else {
                            Icons.Outlined.VisibilityOff
                        },
                        contentDescription = stringResource(KMR.strings.action_hide),
                    )
                },
            )
            // KMK <--
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = stringResource(MR.strings.action_delete))
            }
        }
    }
}
