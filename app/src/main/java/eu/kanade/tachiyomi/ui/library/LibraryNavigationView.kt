package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.util.AttributeSet
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues.DisplayMode
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.MultiSort.Companion.SORT_ASC
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.MultiSort.Companion.SORT_DESC
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.MultiSort.Companion.SORT_NONE
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.Companion.STATE_EXCLUDE
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.Companion.STATE_IGNORE
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.Companion.STATE_INCLUDE
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * The navigation view shown in a drawer with the different options to show the library.
 */
class LibraryNavigationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ExtendedNavigationView(context, attrs) {

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * List of groups shown in the view.
     */
    private val groups = listOf(FilterGroup(), SortGroup(), DisplayGroup(), ButtonsGroup(), BadgeGroup())

    /**
     * Adapter instance.
     */
    private val adapter = Adapter(groups.map { it.createItems() }.flatten())

    /**
     * Click listener to notify the parent fragment when an item from a group is clicked.
     */
    var onGroupClicked: (Group) -> Unit = {}

    init {
        recycler.adapter = adapter
        addView(recycler)

        groups.forEach { it.initModels() }
    }

    /**
     * Returns true if there's at least one filter from [FilterGroup] active.
     */
    fun hasActiveFilters(): Boolean {
        return (groups[0] as FilterGroup).items.any { it.state != STATE_IGNORE } // j2k it.checked -> this
    }

    /**
     * Adapter of the recycler view.
     */
    inner class Adapter(items: List<Item>) : ExtendedNavigationView.Adapter(items) {

        override fun onItemClicked(item: Item) {
            if (item is GroupedItem) {
                item.group.onItemClicked(item)
                onGroupClicked(item.group)
            }
        }
    }

    /**
     * Filters group (unread, downloaded, ...).
     */
    inner class FilterGroup : Group {

        private val downloaded = Item.TriStateGroup(R.string.action_filter_downloaded, this)

        private val unread = Item.TriStateGroup(R.string.action_filter_unread, this)

        private val completed = Item.TriStateGroup(R.string.completed, this)

        private val tracked = Item.TriStateGroup(R.string.action_filter_tracked, this)

        private val lewd = Item.TriStateGroup(R.string.lewd, this)

        override val items = (
            if (Injekt.get<TrackManager>().hasLoggedServices()) {
                listOf(downloaded, unread, completed, tracked, lewd)
            } else {
                listOf(downloaded, unread, completed, lewd)
            }
            )

        override val header = Item.Header(R.string.action_filter)

        override val footer = Item.Separator()

        override fun initModels() { // j2k changes
            try {
                downloaded.state = preferences.filterDownloaded().get()
                unread.state = preferences.filterUnread().get()
                completed.state = preferences.filterCompleted().get()
                if (Injekt.get<TrackManager>().hasLoggedServices()) {
                    tracked.state = preferences.filterTracked().get()
                } else {
                    tracked.state = STATE_IGNORE
                }
                lewd.state = preferences.filterLewd().get()
            } catch (e: Exception) {
                preferences.upgradeFilters()
            }
        }

        override fun onItemClicked(item: Item) { // j2k changes
            item as Item.TriStateGroup
            val newState = when (item.state) {
                STATE_IGNORE -> STATE_INCLUDE
                STATE_INCLUDE -> STATE_EXCLUDE
                else -> STATE_IGNORE
            }
            item.state = newState
            when (item) {
                downloaded -> preferences.filterDownloaded().set(item.state)
                unread -> preferences.filterUnread().set(item.state)
                completed -> preferences.filterCompleted().set(item.state)
                tracked -> preferences.filterTracked().set(item.state)
                lewd -> preferences.filterLewd().set(item.state)
            }

            adapter.notifyItemChanged(item)
        }
    }

    /**
     * Sorting group (alphabetically, by last read, ...) and ascending or descending.
     */
    inner class SortGroup : Group {

        private val alphabetically = Item.MultiSort(R.string.action_sort_alpha, this)

        private val total = Item.MultiSort(R.string.action_sort_total, this)

        private val lastRead = Item.MultiSort(R.string.action_sort_last_read, this)

        private val lastChecked = Item.MultiSort(R.string.action_sort_last_checked, this)

        private val unread = Item.MultiSort(R.string.action_filter_unread, this)

        private val source = Item.MultiSort(R.string.manga_info_source_label, this)

        private val dragAndDrop = Item.MultiSort(R.string.action_sort_drag_and_drop, this)

        private val latestChapter = Item.MultiSort(R.string.action_sort_latest_chapter, this)

        private val dateAdded = Item.MultiSort(R.string.action_sort_date_added, this)

        override val items = listOf(alphabetically, lastRead, lastChecked, unread, total, latestChapter, dateAdded, source, dragAndDrop)

        override val header = Item.Header(R.string.action_sort)

        override val footer = Item.Separator()

        override fun initModels() {
            val sorting = preferences.librarySortingMode().get()
            val order = if (preferences.librarySortingAscending().get()) {
                SORT_ASC
            } else {
                SORT_DESC
            }

            alphabetically.state = if (sorting == LibrarySort.ALPHA) order else SORT_NONE
            lastRead.state = if (sorting == LibrarySort.LAST_READ) order else SORT_NONE
            lastChecked.state = if (sorting == LibrarySort.LAST_CHECKED) order else SORT_NONE
            unread.state = if (sorting == LibrarySort.UNREAD) order else SORT_NONE
            total.state = if (sorting == LibrarySort.TOTAL) order else SORT_NONE
            latestChapter.state = if (sorting == LibrarySort.LATEST_CHAPTER) order else SORT_NONE
            dateAdded.state = if (sorting == LibrarySort.DATE_ADDED) order else Item.MultiSort.SORT_NONE
            source.state = if (sorting == LibrarySort.SOURCE) order else SORT_NONE
            dragAndDrop.state = if (sorting == LibrarySort.DRAG_AND_DROP) order else SORT_NONE
        }

        override fun onItemClicked(item: Item) {
            item as Item.MultiStateGroup
            val prevState = item.state

            item.group.items.forEach { (it as Item.MultiStateGroup).state = SORT_NONE }
            if (item == dragAndDrop) {
                item.state = SORT_ASC
            } else {
                item.state = when (prevState) {
                    SORT_NONE -> SORT_ASC
                    SORT_ASC -> SORT_DESC
                    SORT_DESC -> SORT_ASC
                    else -> throw Exception("Unknown state")
                }
            }

            preferences.librarySortingMode().set(
                when (item) {
                    alphabetically -> LibrarySort.ALPHA
                    lastRead -> LibrarySort.LAST_READ
                    lastChecked -> LibrarySort.LAST_CHECKED
                    unread -> LibrarySort.UNREAD
                    total -> LibrarySort.TOTAL
                    latestChapter -> LibrarySort.LATEST_CHAPTER
                    dateAdded -> LibrarySort.DATE_ADDED
                    source -> LibrarySort.SOURCE
                    dragAndDrop -> LibrarySort.DRAG_AND_DROP
                    else -> throw Exception("Unknown sorting")
                }
            )
            preferences.librarySortingAscending().set(item.state == SORT_ASC)

            item.group.items.forEach { adapter.notifyItemChanged(it) }
        }
    }

    inner class BadgeGroup : Group {
        private val downloadBadge = Item.CheckboxGroup(R.string.action_display_download_badge, this)
        private val unreadBadge = Item.CheckboxGroup(R.string.action_display_unread_badge, this)
        override val header = null
        override val footer = null
        override val items = listOf(downloadBadge, unreadBadge)
        override fun initModels() {
            downloadBadge.checked = preferences.downloadBadge().get()
            unreadBadge.checked = preferences.unreadBadge().get()
        }

        override fun onItemClicked(item: Item) {
            item as Item.CheckboxGroup
            item.checked = !item.checked
            when (item) {
                downloadBadge -> preferences.downloadBadge().set((item.checked))
                unreadBadge -> preferences.unreadBadge().set((item.checked))
            }
            adapter.notifyItemChanged(item)
        }
    }

    // SY -->
    inner class ButtonsGroup : Group {
        private val startReadingButton = Item.CheckboxGroup(R.string.action_start_reading_button, this)

        override val header = Item.Header(R.string.buttons_header)
        override val items = listOf(startReadingButton)
        override val footer = null

        override fun initModels() {
            startReadingButton.checked = preferences.startReadingButton().get()
        }

        override fun onItemClicked(item: Item) {
            item as Item.CheckboxGroup
            item.checked = !item.checked
            when (item) {
                startReadingButton -> preferences.startReadingButton().set((item.checked))
            }
            adapter.notifyItemChanged(item)
        }
    }
    // SY <--

    /**
     * Display group, to show the library as a list or a grid.
     */
    inner class DisplayGroup : Group {

        private val compactGrid = Item.Radio(R.string.action_display_grid, this)

        private val comfortableGrid = Item.Radio(R.string.action_display_comfortable_grid, this)

        private val list = Item.Radio(R.string.action_display_list, this)

        override val items = listOf(compactGrid, comfortableGrid, list)

        override val header = Item.Header(R.string.action_display)

        override val footer = null

        override fun initModels() {
            val mode = preferences.libraryDisplayMode().get()
            compactGrid.checked = mode == DisplayMode.COMPACT_GRID
            comfortableGrid.checked = mode == DisplayMode.COMFORTABLE_GRID
            list.checked = mode == DisplayMode.LIST
        }

        override fun onItemClicked(item: Item) {
            item as Item.Radio
            if (item.checked) return

            item.group.items.forEach { (it as Item.Radio).checked = false }
            item.checked = true

            preferences.libraryDisplayMode().set(
                when (item) {
                    compactGrid -> DisplayMode.COMPACT_GRID
                    comfortableGrid -> DisplayMode.COMFORTABLE_GRID
                    list -> DisplayMode.LIST
                    else -> throw NotImplementedError("Unknown display mode")
                }
            )

            item.group.items.forEach { adapter.notifyItemChanged(it) }
        }
    }
}
