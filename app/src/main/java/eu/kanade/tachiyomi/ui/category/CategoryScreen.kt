package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.CategoryScreen
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.category.model.Category
import tachiyomi.presentation.core.screens.LoadingScreen

class CategoryScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { CategoryScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is CategoryScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as CategoryScreenState.Success

        CategoryScreen(
            state = successState,
            onClickCreate = { screenModel.showDialog(CategoryDialog.Create) },
            onClickRename = { screenModel.showDialog(CategoryDialog.Rename(it)) },
            onClickDelete = { screenModel.showDialog(CategoryDialog.Delete(it)) },
            onChangeOrder = screenModel::changeOrder,
            onChangeParent = screenModel::changeParent,
            // KMK -->
            onClickHide = screenModel::hideCategory,
            // KMK <--
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            CategoryDialog.Create -> {
                CategoryCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = screenModel::createCategory,
                    categories = successState.categories.fastMap { it.name }.toImmutableList(),
                    parentOptions = successState.categories
                        .filter { it.parentId == null }
                        .filterNot { it.isSystemCategory }
                        .toImmutableList(),
                )
            }
            is CategoryDialog.Rename -> {
                CategoryRenameDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onRename = { newName, parentId -> screenModel.renameCategory(dialog.category, newName, parentId) },
                    categories = successState.categories.fastMap { it.name }.toImmutableList(),
                    category = dialog.category.name,
                    parentOptions = successState.categories
                        .filterNot { candidate ->
                            // Can't be: itself, a system category, a descendant of this category, or a subcategory
                            candidate.id == dialog.category.id ||
                            candidate.isSystemCategory ||
                            candidate.parentId != null ||  // Exclude subcategories (only show parent categories)
                            isDescendantOf(candidate, dialog.category, successState.categories)
                        }
                        .toImmutableList(),
                    initialParentId = dialog.category.parentId,
                    categoryHasChildren = hasCategoryChildren(dialog.category, successState.categories),
                )
            }
            is CategoryDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteCategory(dialog.category.id) },
                    category = dialog.category.name,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is CategoryEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}

private fun hasCategoryChildren(category: Category, allCategories: List<Category>): Boolean {
    return allCategories.any { it.parentId == category.id }
}

private fun isDescendantOf(candidate: Category, parent: Category, allCategories: List<Category>): Boolean {
    var currentParentId = candidate.parentId
    while (currentParentId != null) {
        if (currentParentId == parent.id) return true
        currentParentId = allCategories.firstOrNull { it.id == currentParentId }?.parentId
    }
    return false
}
