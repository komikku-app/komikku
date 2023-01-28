package eu.kanade.tachiyomi.ui.category.sources

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.domain.source.interactor.CreateSourceCategory
import eu.kanade.domain.source.interactor.DeleteSourceCategory
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.RenameSourceCategory
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourceCategoryScreenModel(
    private val getSourceCategories: GetSourceCategories = Injekt.get(),
    private val createSourceCategory: CreateSourceCategory = Injekt.get(),
    private val renameSourceCategory: RenameSourceCategory = Injekt.get(),
    private val deleteSourceCategory: DeleteSourceCategory = Injekt.get(),
) : StateScreenModel<SourceCategoryScreenState>(SourceCategoryScreenState.Loading) {

    private val _events: Channel<SourceCategoryEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        coroutineScope.launchIO {
            getSourceCategories.subscribe()
                .collectLatest { categories ->
                    mutableState.update {
                        SourceCategoryScreenState.Success(
                            categories = categories,
                        )
                    }
                }
        }
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createCategory(name: String) {
        coroutineScope.launchIO {
            when (createSourceCategory.await(name)) {
                is CreateSourceCategory.Result.InvalidName -> _events.send(SourceCategoryEvent.InvalidName)
                else -> {}
            }
        }
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param categories The list of categories to delete.
     */
    fun deleteCategory(categories: String) {
        coroutineScope.launchIO {
            deleteSourceCategory.await(categories)
        }
    }

    /**
     * Renames a category.
     *
     * @param categoryOld The category to rename.
     * @param categoryNew The new name of the category.
     */
    fun renameCategory(categoryOld: String, categoryNew: String) {
        coroutineScope.launchIO {
            when (renameSourceCategory.await(categoryOld, categoryNew)) {
                is CreateSourceCategory.Result.InvalidName -> _events.send(SourceCategoryEvent.InvalidName)
                else -> {}
            }
        }
    }

    fun showDialog(dialog: SourceCategoryDialog) {
        mutableState.update {
            when (it) {
                SourceCategoryScreenState.Loading -> it
                is SourceCategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                SourceCategoryScreenState.Loading -> it
                is SourceCategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class SourceCategoryEvent {
    sealed class LocalizedMessage(@StringRes val stringRes: Int) : SourceCategoryEvent()
    object InvalidName : LocalizedMessage(R.string.invalid_category_name)
    object InternalError : LocalizedMessage(R.string.internal_error)
}

sealed class SourceCategoryDialog {
    object Create : SourceCategoryDialog()
    data class Rename(val category: String) : SourceCategoryDialog()
    data class Delete(val category: String) : SourceCategoryDialog()
}

sealed class SourceCategoryScreenState {

    @Immutable
    object Loading : SourceCategoryScreenState()

    @Immutable
    data class Success(
        val categories: List<String>,
        val dialog: SourceCategoryDialog? = null,
    ) : SourceCategoryScreenState() {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
