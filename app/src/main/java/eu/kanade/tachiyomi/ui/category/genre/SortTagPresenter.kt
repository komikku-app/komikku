package eu.kanade.tachiyomi.ui.category.genre

import android.os.Bundle
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.preference.minusAssign
import eu.kanade.tachiyomi.util.preference.plusAssign
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
    private var tags: List<Pair<Int, String>> = emptyList()

    val preferences: PreferencesHelper = Injekt.get()

    /**
     * Called when the presenter is created.
     *
     * @param savedState The saved state of this presenter.
     */
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        preferences.sortTagsForLibrary().asFlow().onEach { tags ->
            this.tags = tags.map { it.split("|") }
                .mapNotNull { (it.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null) to (it.getOrNull(1) ?: return@mapNotNull null) }
                .sortedBy { it.first }

            Observable.just(this.tags)
                .map { tagPairs -> tagPairs.map { it.second }.map(::SortTagItem) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeLatestCache(SortTagController::setCategories)
        }.launchIn(presenterScope)
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createTag(name: String) {
        // Do not allow duplicate categories.
        if (tagExists(name.trim())) {
            Observable.just(Unit).subscribeFirst({ view, _ -> view.onTagExistsError() })
            return
        }

        val size = preferences.sortTagsForLibrary().get().size

        preferences.sortTagsForLibrary() += "$size|${name.trim()}"
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param tags The list of categories to delete.
     */
    fun deleteTags(tags: List<String>) {
        val preferenceTags = preferences.sortTagsForLibrary().get()
        tags.forEach { tag ->
            preferenceTags.firstOrNull { it.endsWith(tag) }?.let {
                preferences.sortTagsForLibrary() -= it
            }
        }
    }

    /**
     * Reorders the given categories in the database.
     *
     * @param tags The list of categories to reorder.
     */
    fun reorderTags(tags: List<String>) {
        preferences.sortTagsForLibrary().set(tags.mapIndexed { index, tag -> "$index|$tag" }.toSet())
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun tagExists(name: String): Boolean {
        return tags.any { it.equals(name) }
    }
}
