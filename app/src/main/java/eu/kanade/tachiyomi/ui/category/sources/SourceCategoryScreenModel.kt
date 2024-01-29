package eu.kanade.tachiyomi.ui.category.sources

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.interactor.CreateSourceCategory
import eu.kanade.domain.source.interactor.DeleteSourceCategory
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.RenameSourceCategory
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
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
        screenModelScope.launchIO {
            getSourceCategories.subscribe()
                .collectLatest { categories ->
                    mutableState.update {
                        SourceCategoryScreenState.Success(
                            categories = categories.toImmutableList(),
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
        screenModelScope.launchIO {
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
        screenModelScope.launchIO {
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
        screenModelScope.launchIO {
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
    sealed class LocalizedMessage(val stringRes: StringResource) : SourceCategoryEvent()
    data object InvalidName : LocalizedMessage(SYMR.strings.invalid_category_name)
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed class SourceCategoryDialog {
    data object Create : SourceCategoryDialog()
    data class Rename(val category: String) : SourceCategoryDialog()
    data class Delete(val category: String) : SourceCategoryDialog()
}

sealed class SourceCategoryScreenState {

    @Immutable
    data object Loading : SourceCategoryScreenState()

    @Immutable
    data class Success(
        val categories: ImmutableList<String>,
        val dialog: SourceCategoryDialog? = null,
    ) : SourceCategoryScreenState() {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
