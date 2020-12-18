package eu.kanade.tachiyomi.ui.extension.repos

import eu.davidea.flexibleadapter.FlexibleAdapter

/**
 * Custom adapter for repos.
 *
 * @param controller The containing controller.
 */
class RepoAdapter(controller: RepoController) :
    FlexibleAdapter<RepoItem>(null, controller, true) {

    /**
     * Clears the active selections from the list and the model.
     */
    override fun clearSelection() {
        super.clearSelection()
        (0 until itemCount).forEach { getItem(it)?.isSelected = false }
    }

    /**
     * Toggles the selection of the given position.
     *
     * @param position The position to toggle.
     */
    override fun toggleSelection(position: Int) {
        super.toggleSelection(position)
        getItem(position)?.isSelected = isSelected(position)
    }
}
