package eu.kanade.tachiyomi.ui.manga.merged

import eu.davidea.flexibleadapter.FlexibleAdapter

/**
 * Adapter storing a list of merged manga.
 *
 * @param controller the context of the fragment containing this adapter.
 * @param isPriorityOrder if deduplication mode is based on priority
 */
class EditMergedMangaAdapter(listener: EditMergedSettingsState, var isPriorityOrder: Boolean) :
    FlexibleAdapter<EditMergedMangaItem>(null, listener, true),
    EditMergedSettingsHeaderAdapter.SortingListener {

    /**
     * Listener called when an item of the list is released.
     */
    val editMergedMangaItemListener: EditMergedMangaItemListener = listener

    interface EditMergedMangaItemListener {
        fun onItemReleased(position: Int)
        fun onDeleteClick(position: Int)
        fun onToggleChapterUpdatesClicked(position: Int)
        fun onToggleChapterDownloadsClicked(position: Int)
    }

    override fun onSetPrioritySort(isPriorityOrder: Boolean) {
        isHandleDragEnabled = isPriorityOrder
        this.isPriorityOrder = isPriorityOrder
        allBoundViewHolders.onEach { editMergedMangaHolder ->
            if (editMergedMangaHolder is EditMergedMangaHolder) {
                editMergedMangaHolder.setHandelAlpha(isPriorityOrder)
            }
        }
    }
}
