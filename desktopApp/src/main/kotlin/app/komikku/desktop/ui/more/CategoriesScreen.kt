package app.komikku.desktop.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.desktop.ui.browse.BrowseScaffold
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.HideCategory
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.interactor.ReorderCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Desktop category management (Android `CategoryScreen` + `CategoryScreenModel`): add, rename, move up /
 * down, hide / show, delete (with confirmation), through the same domain interactors; duplicate names are
 * rejected like on Android.
 */
class CategoriesScreenModel(
    private val getCategories: GetCategories = Injekt.get(),
    private val createCategory: CreateCategoryWithName = Injekt.get(),
    private val renameCategory: RenameCategory = Injekt.get(),
    private val reorderCategory: ReorderCategory = Injekt.get(),
    private val hideCategory: HideCategory = Injekt.get(),
    private val deleteCategory: DeleteCategory = Injekt.get(),
) : StateScreenModel<CategoriesScreenModel.State>(State()) {

    @Immutable
    data class State(val categories: List<Category> = emptyList(), val dialog: Dialog? = null)

    sealed interface Dialog {
        data object Create : Dialog
        data class Rename(val category: Category) : Dialog
        data class Delete(val category: Category) : Dialog
    }

    init {
        screenModelScope.launch {
            getCategories.subscribe().collectLatest { all ->
                mutableState.update { it.copy(categories = all.filterNot(Category::isSystemCategory).sortedBy { c -> c.order }) }
            }
        }
    }

    fun nameTaken(name: String, except: Category? = null) =
        state.value.categories.any { it.name.equals(name.trim(), ignoreCase = true) && it.id != except?.id }

    fun create(name: String) = run { withIOContext { createCategory.await(name.trim()) } }

    fun rename(category: Category, name: String) = run { withIOContext { renameCategory.await(category, name.trim()) } }

    fun move(category: Category, offset: Int) {
        val index = state.value.categories.indexOfFirst { it.id == category.id }
        val target = (index + offset).coerceIn(0, state.value.categories.lastIndex)
        if (index < 0 || target == index) return
        screenModelScope.launch { withIOContext { reorderCategory.await(category, target) } }
    }

    fun toggleHidden(category: Category) = screenModelScope.launch { withIOContext { hideCategory.await(category) } }

    fun delete(category: Category) = run { withIOContext { deleteCategory.await(category.id) } }

    fun show(dialog: Dialog?) = mutableState.update { it.copy(dialog = dialog) }

    private fun run(block: suspend () -> Unit) {
        show(null)
        screenModelScope.launch { block() }
    }
}

class CategoriesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { CategoriesScreenModel() }
        val state by model.state.collectAsState()
        BrowseScaffold(title = stringResource(MR.strings.action_edit_categories), onBack = navigator::pop) {
            Box(Modifier.fillMaxSize()) {
                if (state.categories.isEmpty()) {
                    EmptyScreen(stringRes = MR.strings.information_empty_category_dialog)
                } else {
                    // Room under the last row for the "Add" button (its height and margins)
                    LazyColumn(Modifier.fillMaxSize().testTag("categories"), contentPadding = PaddingValues(bottom = 88.dp)) {
                        itemsIndexed(state.categories, key = { _, it -> it.id }) { index, category ->
                            CategoryRow(category, index, state.categories.size, model)
                        }
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = { model.show(CategoriesScreenModel.Dialog.Create) },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(MR.strings.action_add)) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("category-add"),
                )
            }
        }
        when (val dialog = state.dialog) {
            CategoriesScreenModel.Dialog.Create -> NameDialog(
                title = stringResource(MR.strings.action_add_category),
                initial = "",
                taken = { model.nameTaken(it) },
                onConfirm = model::create,
                onDismiss = { model.show(null) },
            )
            is CategoriesScreenModel.Dialog.Rename -> NameDialog(
                title = stringResource(MR.strings.action_rename_category),
                initial = dialog.category.name,
                taken = { model.nameTaken(it, dialog.category) },
                onConfirm = { model.rename(dialog.category, it) },
                onDismiss = { model.show(null) },
            )
            is CategoriesScreenModel.Dialog.Delete -> AlertDialog(
                onDismissRequest = { model.show(null) },
                title = { Text(stringResource(MR.strings.delete_category)) },
                text = { Text(stringResource(MR.strings.delete_category_confirmation, dialog.category.name)) },
                confirmButton = {
                    TextButton(onClick = { model.delete(dialog.category) }, modifier = Modifier.testTag("category-delete-confirm")) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = { TextButton(onClick = { model.show(null) }) { Text(stringResource(MR.strings.action_cancel)) } },
            )
            null -> {}
        }
    }
}

@Composable
private fun CategoryRow(category: Category, index: Int, count: Int, model: CategoriesScreenModel) {
    ListItem(
        modifier = Modifier.testTag("category-${category.name}"),
        headlineContent = { Text(category.name, modifier = Modifier.alpha(if (category.hidden) 0.5f else 1f)) },
        trailingContent = {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { model.move(category, -1) }, enabled = index > 0, modifier = Modifier.testTag("category-up-${category.name}")) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(MR.strings.action_move_to_top))
                }
                IconButton(onClick = { model.move(category, 1) }, enabled = index < count - 1, modifier = Modifier.testTag("category-down-${category.name}")) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(MR.strings.action_move_to_bottom))
                }
                IconButton(onClick = { model.toggleHidden(category) }, modifier = Modifier.testTag("category-hide-${category.name}")) {
                    Icon(if (category.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null)
                }
                IconButton(
                    onClick = { model.show(CategoriesScreenModel.Dialog.Rename(category)) },
                    modifier = Modifier.testTag("category-rename-${category.name}"),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(MR.strings.action_rename_category))
                }
                IconButton(
                    onClick = { model.show(CategoriesScreenModel.Dialog.Delete(category)) },
                    modifier = Modifier.testTag("category-delete-${category.name}"),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(MR.strings.action_delete))
                }
            }
        },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, taken: (String) -> Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val duplicate = name.isNotBlank() && name.trim() != initial && taken(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(MR.strings.name)) },
                    isError = duplicate,
                    supportingText = { if (duplicate) Text(stringResource(MR.strings.error_category_exists)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("category-name"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && !duplicate && name.trim() != initial,
                modifier = Modifier.testTag("category-name-confirm"),
            ) { Text(stringResource(MR.strings.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}
