package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.CategoryScreen
import eu.kanade.presentation.category.buildCategoryDescendants
import eu.kanade.presentation.category.buildCategoryHierarchy
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
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

        // KMK --> Add expand/collapse state management
        val expanded = rememberSaveable { mutableStateOf(setOf<Long>()) }

        fun toggle(categoryId: Long) {
            expanded.value =
                if (expanded.value.contains(categoryId)) {
                    expanded.value - categoryId
                } else {
                    expanded.value + categoryId
                }
        }
        // KMK <--

        CategoryScreen(
            state = successState,
            onClickCreate = { screenModel.showDialog(CategoryDialog.Create) },
            onClickRename = { screenModel.showDialog(CategoryDialog.Rename(it)) },
            onClickDelete = { screenModel.showDialog(CategoryDialog.Delete(it)) },
            onChangeOrder = screenModel::changeOrder,
            // KMK -->
            onClickHide = screenModel::hideCategory,
            expanded = expanded.value,
            onToggleExpand = ::toggle,
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
                        .filterNot { it.isSystemCategory }
                        .let(::buildCategoryHierarchy)
                        .map { it.category }
                        .toImmutableList(),
                )
            }
            is CategoryDialog.Rename -> {
                val invalidParentIds = remember(dialog.category.id, successState.categories) {
                    buildCategoryDescendants(successState.categories)[dialog.category.id]
                        .orEmpty()
                        .mapTo(mutableSetOf(dialog.category.id)) { it.id }
                }
                CategoryRenameDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onRename = { newName, parentId -> screenModel.renameCategory(dialog.category, newName, parentId) },
                    categories = successState.categories.fastMap { it.name }.toImmutableList(),
                    category = dialog.category.name,
                    parentOptions = successState.categories
                        .filterNot { candidate ->
                            candidate.isSystemCategory || candidate.id in invalidParentIds
                        }
                        .let(::buildCategoryHierarchy)
                        .map { it.category }
                        .toImmutableList(),
                    initialParentId = dialog.category.parentId,
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
