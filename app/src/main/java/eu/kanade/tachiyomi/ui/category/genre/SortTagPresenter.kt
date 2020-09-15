package eu.kanade.tachiyomi.ui.category.genre

import android.os.Bundle
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.minusAssign
import eu.kanade.tachiyomi.data.preference.plusAssign
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [SortTagController]. Used to manage the categories of the library.
 */
class SortTagPresenter : BasePresenter<SortTagController>() {

    /**
     * List containing categories.
     */
    private var tags: List<String> = emptyList()

    val preferences: PreferencesHelper = Injekt.get()

    val scope = CoroutineScope(Job() + Dispatchers.Main)

    /**
     * Called when the presenter is created.
     *
     * @param savedState The saved state of this presenter.
     */
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        preferences.sortTagsForLibrary().asFlow().onEach { tags ->
            this.tags = tags.toList()

            Observable.just(this.tags)
                .map { it.map(::SortTagItem) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeLatestCache(SortTagController::setCategories)
        }.launchIn(scope)
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createCategory(name: String) {
        // Do not allow duplicate categories.
        if (tagExists(name)) {
            Observable.just(Unit).subscribeFirst({ view, _ -> view.onTagExistsError() })
            return
        }

        preferences.sortTagsForLibrary() += name
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param categories The list of categories to delete.
     */
    fun deleteTags(categories: List<String>) {
        categories.forEach {
            preferences.sortTagsForLibrary() -= it
        }
    }

    /**
     * Reorders the given categories in the database.
     *
     * @param categories The list of categories to reorder.
     */
    fun reorderTags(categories: List<String>) {
        preferences.sortTagsForLibrary().set(categories.toSet())
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun tagExists(name: String): Boolean {
        return tags.any { it.equals(name, true) }
    }
}
