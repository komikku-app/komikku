package app.komikku.desktop.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.browse.BrowseScaffold
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryDialog
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryEvent
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryScreenModel
import eu.kanade.tachiyomi.ui.category.sources.SourceCategoryScreenState
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * P21: SY's source categories screen (Android's `SourceCategoryScreen`, Settings → Browse → "Edit categories") on the
 * borrowed [SourceCategoryScreenModel]: the categories of the sources tab by name, add, rename and delete with Android's
 * dialogs (`CategoryDialogs.kt`; duplicate names refused there, a "|" refused by the interactor with SY's message). A
 * source goes into categories from the sources tab (its options dialog → "Categories").
 */
class SourceCategoriesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val model = rememberScreenModel { SourceCategoryScreenModel() }
        val state by model.state.collectAsState()

        LaunchedEffect(model) {
            model.events.collect { event ->
                if (event is SourceCategoryEvent.LocalizedMessage) context.toast(event.stringRes)
            }
        }

        BrowseScaffold(title = stringResource(MR.strings.action_edit_categories), onBack = navigator::pop) {
            val current = state
            if (current !is SourceCategoryScreenState.Success) {
                LoadingScreen()
                return@BrowseScaffold
            }
            Box(Modifier.fillMaxSize()) {
                if (current.isEmpty) {
                    EmptyScreen(stringRes = MR.strings.information_empty_category_dialog)
                } else {
                    // Room under the last row for the "Add" button (its height and margins)
                    LazyColumn(Modifier.fillMaxSize().testTag("source-categories"), contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(current.categories, key = { it }) { category ->
                            ListItem(
                                modifier = Modifier.testTag("source-category-$category"),
                                headlineContent = { Text(category) },
                                trailingContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { model.showDialog(SourceCategoryDialog.Rename(category)) },
                                            modifier = Modifier.testTag("source-category-rename-$category"),
                                        ) {
                                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(MR.strings.action_rename_category))
                                        }
                                        IconButton(
                                            onClick = { model.showDialog(SourceCategoryDialog.Delete(category)) },
                                            modifier = Modifier.testTag("source-category-delete-$category"),
                                        ) {
                                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(MR.strings.action_delete))
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = { model.showDialog(SourceCategoryDialog.Create) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(MR.strings.action_add)) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("source-category-add"),
                )
            }
        }

        val success = state as? SourceCategoryScreenState.Success ?: return
        when (val dialog = success.dialog) {
            null -> {}
            SourceCategoryDialog.Create -> CategoryCreateDialog(
                onDismissRequest = model::dismissDialog,
                onCreate = { model.createCategory(it) },
                categories = success.categories,
                title = stringResource(MR.strings.action_add_category),
            )
            is SourceCategoryDialog.Rename -> CategoryRenameDialog(
                onDismissRequest = model::dismissDialog,
                onRename = { model.renameCategory(dialog.category, it) },
                categories = success.categories,
                category = dialog.category,
            )
            is SourceCategoryDialog.Delete -> CategoryDeleteDialog(
                onDismissRequest = model::dismissDialog,
                onDelete = { model.deleteCategory(dialog.category) },
                title = stringResource(MR.strings.delete_category),
                text = stringResource(MR.strings.delete_category_confirmation, dialog.category),
            )
        }
    }
}
