package eu.kanade.tachiyomi.ui.category.sources

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [SourceCategoryController]. Used to manage the categories of the library.
 */
class SourceCategoryPresenter(
    private val db: DatabaseHelper = Injekt.get()
) : BasePresenter<SourceCategoryController>() {

    /**
     * List containing categories.
     */
    private var categories: List<String> = emptyList()

    val preferences: PreferencesHelper = Injekt.get()

    /**
     * Called when the presenter is created.
     *
     * @param savedState The saved state of this presenter.
     */
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        preferences.sourcesTabCategories().asFlow().onEach { categories ->
            this.categories = categories.toList().sortedBy { it.lowercase() }

            Observable.just(this.categories)
                .map { it.map(::SourceCategoryItem) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeLatestCache(SourceCategoryController::setCategories)
        }.launchIn(presenterScope)
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createCategory(name: String) {
        // Do not allow duplicate categories.
        if (categoryExists(name)) {
            Observable.just(Unit).subscribeFirst({ view, _ -> view.onCategoryExistsError() })
            return
        }

        if (name.contains("|")) {
            Observable.just(Unit).subscribeFirst({ view, _ -> view.onCategoryInvalidNameError() })
            return
        }

        // Create category.
        val newCategories = categories.toMutableList()
        newCategories += name

        preferences.sourcesTabCategories().set(newCategories.toSet())
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param categories The list of categories to delete.
     */
    fun deleteCategories(categories: List<String>) {
        var sources = preferences.sourcesTabSourcesInCategories().get().toList()

        sources = sources.map { it.split("|") }.filterNot { it[1] in categories }.map { it[0] + "|" + it[1] }

        preferences.sourcesTabSourcesInCategories().set(sources.toSet())
        preferences.sourcesTabCategories().set(
            this.categories.filterNot { it in categories }.toSet()
        )
    }

    /**
     * Renames a category.
     *
     * @param category The category to rename.
     * @param name The new name of the category.
     */
    fun renameCategory(categoryOld: String, categoryNew: String) {
        // Do not allow duplicate categories.
        if (categoryExists(categoryNew)) {
            Observable.just(Unit).subscribeFirst({ view, _ -> view.onCategoryExistsError() })
            return
        }

        if (categoryNew.contains("|")) {
            Observable.just(Unit).subscribeFirst({ view, _ -> view.onCategoryInvalidNameError() })
            return
        }

        val newCategories = categories.filterNot { it in categoryOld }.toMutableList()
        newCategories += categoryNew

        var sources = preferences.sourcesTabSourcesInCategories().get().toList()

        sources = sources.map { it.split("|").toMutableList() }
            .map {
                if (it[1] == categoryOld) {
                    it[1] = categoryNew
                }
                it[0] + "|" + it[1]
            }

        preferences.sourcesTabSourcesInCategories().set(sources.toSet())
        preferences.sourcesTabCategories().set(newCategories.sorted().toSet())
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun categoryExists(name: String): Boolean {
        return categories.any { it.equals(name, true) }
    }
}
