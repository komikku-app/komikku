package eu.kanade.presentation.category.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReorderableCollectionItemScope.CategoryListItem(
    category: Category,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    // KMK -->
    onHide: () -> Unit,
    indentLevel: Int = 0,
    hasChildren: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onDragStopped: () -> Unit = {},
    // KMK <--
    modifier: Modifier = Modifier,
) {
    val startPadding = MaterialTheme.padding.small + (indentLevel.coerceIn(0, 4) * 16).dp

    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRename)
                .padding(vertical = MaterialTheme.padding.small)
                .padding(
                    start = startPadding,
                    end = MaterialTheme.padding.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = stringResource(KMR.strings.action_reorder_category),
                modifier = Modifier
                    .padding(MaterialTheme.padding.medium)
                    .draggableHandle(onDragStopped = onDragStopped),
            )
            // KMK -->
            if (indentLevel > 0) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(1.dp),
                        ),
                )
            }
            // KMK <--
            Text(
                text = category.name,
                // KMK -->
                color = LocalContentColor.current.let { if (category.hidden) it.copy(alpha = 0.6f) else it },
                textDecoration = TextDecoration.LineThrough.takeIf { category.hidden },
                // KMK <--
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (indentLevel > 0) 8.dp else 0.dp),
            )
            // KMK -->
            if (hasChildren) {
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Filled.KeyboardArrowDown
                        } else {
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        },
                        contentDescription = stringResource(
                            if (isExpanded) {
                                KMR.strings.action_collapse_category
                            } else {
                                KMR.strings.action_expand_category
                            },
                        ),
                    )
                }
            }
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_rename_category)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (category.hidden) KMR.strings.action_show else KMR.strings.action_hide,
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (category.hidden) {
                                    Icons.Outlined.Visibility
                                } else {
                                    Icons.Outlined.VisibilityOff
                                },
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onHide()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
            // KMK <--
        }
    }
}
