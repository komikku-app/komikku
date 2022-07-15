package eu.kanade.tachiyomi.ui.category.sources

import android.os.Bundle
import eu.kanade.domain.source.interactor.CreateSourceCategory
import eu.kanade.domain.source.interactor.DeleteSourceCategory
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.RenameSourceCategory
import eu.kanade.presentation.category.SourceCategoryState
import eu.kanade.presentation.category.SourceCategoryStateImpl
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [SourceCategoryController]. Used to manage the categories of the library.
 */
class SourceCategoryPresenter(
    private val state: SourceCategoryStateImpl = SourceCategoryState() as SourceCategoryStateImpl,
    private val getSourceCategories: GetSourceCategories = Injekt.get(),
    private val createSourceCategory: CreateSourceCategory = Injekt.get(),
    private val renameSourceCategory: RenameSourceCategory = Injekt.get(),
    private val deleteSourceCategory: DeleteSourceCategory = Injekt.get(),
) : BasePresenter<SourceCategoryController>(), SourceCategoryState by state {

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events = _events.consumeAsFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenterScope.launchIO {
            getSourceCategories.subscribe()
                .collectLatest {
                    state.isLoading = false
                    state.categories = it
                }
        }
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createCategory(name: String) {
        presenterScope.launchIO {
            when (createSourceCategory.await(name)) {
                is CreateSourceCategory.Result.CategoryExists -> _events.send(Event.CategoryExists)
                is CreateSourceCategory.Result.InvalidName -> _events.send(Event.InvalidName)
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
        presenterScope.launchIO {
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
        presenterScope.launchIO {
            when (renameSourceCategory.await(categoryOld, categoryNew)) {
                is CreateSourceCategory.Result.CategoryExists -> _events.send(Event.CategoryExists)
                is CreateSourceCategory.Result.InvalidName -> _events.send(Event.InvalidName)
                else -> {}
            }
        }
    }

    sealed class Event {
        object CategoryExists : Event()
        object InvalidName : Event()
        object InternalError : Event()
    }

    sealed class Dialog {
        object Create : Dialog()
        data class Rename(val category: String) : Dialog()
        data class Delete(val category: String) : Dialog()
    }
}
