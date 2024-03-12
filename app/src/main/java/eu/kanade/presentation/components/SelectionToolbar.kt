package eu.kanade.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SelectionToolbar(
    selectedCount: Int,
    onClickClearSelection: () -> Unit,
    onChangeCategoryClicked: () -> Unit,
    onSelectAll: () -> Unit = {},
) {
    AppBar(
        titleContent = { Text(text = "$selectedCount") },
        actions = {
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_bookmark),
                        icon = Icons.Filled.BookmarkAdd,
                        onClick = {
                            if (selectedCount > 0) {
                                onChangeCategoryClicked()
                            }
                        },
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_all),
                        icon = Icons.Filled.SelectAll,
                        onClick = {
                            onSelectAll.invoke()
                        },
                    ),
                ),
            )
        },
        isActionMode = true,
        onCancelActionMode = onClickClearSelection,
    )
}

@Preview
@Composable
fun SelectionToolbarPreview() {
    SelectionToolbar(
        selectedCount = 9,
        {},
        {},
    )
}
