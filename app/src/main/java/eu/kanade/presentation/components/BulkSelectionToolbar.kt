package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.m3e.ExpressiveFloatingToolbar
import tachiyomi.presentation.core.components.m3e.FloatingToolbarAction
import tachiyomi.presentation.core.components.m3e.WavyIndeterminateCircularProgressIndicator
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The toolbar for a bulk selection of library entries.
 *
 * The toolbar is a floating toolbar, so give it the `bottomBar` slot of the screen scaffold. It
 * carries the count, the actions for the selection, and the action that clears the selection.
 */
@Composable
fun BulkSelectionToolbar(
    selectedCount: Int,
    isRunning: Boolean,
    onClickClearSelection: () -> Unit,
    onChangeCategoryClick: () -> Unit,
    onSelectAll: (() -> Unit)? = null,
    onReverseSelection: (() -> Unit)? = null,
) {
    ExpressiveFloatingToolbar(
        expanded = true,
        leadingContent = {
            Text(
                text = "$selectedCount",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        trailingContent = {
            if (isRunning) {
                WavyIndeterminateCircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                )
            } else {
                FloatingToolbarAction(
                    icon = Icons.Filled.Favorite,
                    title = stringResource(MR.strings.add_to_library),
                    onClick = {
                        if (selectedCount > 0) {
                            onChangeCategoryClick()
                        }
                    },
                )
            }
            FloatingToolbarAction(
                icon = Icons.Outlined.Close,
                title = stringResource(MR.strings.action_cancel),
                onClick = onClickClearSelection,
            )
        },
    ) {
        if (onSelectAll != null) {
            FloatingToolbarAction(
                icon = Icons.Filled.SelectAll,
                title = stringResource(MR.strings.action_select_all),
                onClick = onSelectAll,
            )
        }
        if (onReverseSelection != null) {
            FloatingToolbarAction(
                icon = Icons.Outlined.FlipToBack,
                title = stringResource(MR.strings.action_select_inverse),
                onClick = onReverseSelection,
            )
        }
    }
}

@Preview
@Composable
private fun SelectionToolbarPreview() {
    Column {
        BulkSelectionToolbar(
            selectedCount = 9,
            isRunning = false,
            onClickClearSelection = {},
            onChangeCategoryClick = {},
            onSelectAll = {},
            onReverseSelection = {},
        )
        BulkSelectionToolbar(
            selectedCount = 9,
            isRunning = true,
            onClickClearSelection = {},
            onChangeCategoryClick = {},
            onSelectAll = {},
            onReverseSelection = {},
        )
    }
}
