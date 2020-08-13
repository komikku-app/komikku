package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferenceValues.DisplayMode
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.category.CategoryAdapter
import eu.kanade.tachiyomi.util.lang.plusAssign
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import exh.ui.LoadingHandle
import kotlinx.android.synthetic.main.library_category.view.fast_scroller
import kotlinx.android.synthetic.main.library_category.view.swipe_refresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import reactivecircus.flowbinding.recyclerview.scrollStateChanges
import reactivecircus.flowbinding.swiperefreshlayout.refreshes
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

/**
 * Fragment containing the library manga for a certain category.
 */
class LibraryCategoryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    // SY -->
    FlexibleAdapter.OnItemMoveListener,
    CategoryAdapter.OnItemReleaseListener {
    // SY <--

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val preferences: PreferencesHelper by injectLazy()

    private val db: DatabaseHelper by injectLazy()

    /**
     * The fragment containing this view.
     */
    private lateinit var controller: LibraryController

    /**
     * Category for this view.
     */
    lateinit var category: Category
        private set

    /**
     * Recycler view of the list of manga.
     */
    private lateinit var recycler: RecyclerView

    /**
     * Adapter to hold the manga in this category.
     */
    private lateinit var adapter: LibraryCategoryAdapter

    /**
     * Subscriptions while the view is bound.
     */
    private var subscriptions = CompositeSubscription()

    private var lastClickPosition = -1

    // EXH -->
    private var initialLoadHandle: LoadingHandle? = null
    private lateinit var supervisorScope: CoroutineScope

    private fun newScope() = object : CoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Main
    }
    // EXH <--

    fun onCreate(controller: LibraryController) {
        this.controller = controller

        recycler = if (preferences.libraryDisplayMode().get() == DisplayMode.LIST) {
            (swipe_refresh.inflate(R.layout.library_list_recycler) as RecyclerView).apply {
                layoutManager = LinearLayoutManager(context)
            }
        } else {
            (swipe_refresh.inflate(R.layout.library_grid_recycler) as AutofitRecyclerView).apply {
                spanCount = controller.mangaPerRow
            }
        }

        adapter = LibraryCategoryAdapter(this, controller)

        recycler.setHasFixedSize(true)
        recycler.adapter = adapter
        swipe_refresh.addView(recycler)
        adapter.fastScroller = fast_scroller

        recycler.scrollStateChanges()
            .onEach {
                // Disable swipe refresh when view is not at the top
                val firstPos = (recycler.layoutManager as LinearLayoutManager)
                    .findFirstCompletelyVisibleItemPosition()
                swipe_refresh.isEnabled = firstPos <= 0
            }
            .launchIn(scope)

        // Double the distance required to trigger sync
        swipe_refresh.setDistanceToTriggerSync((2 * 64 * resources.displayMetrics.density).toInt())
        swipe_refresh.refreshes()
            .onEach {
                if (LibraryUpdateService.start(context, if (preferences.groupLibraryBy().get() == LibraryGroup.BY_DEFAULT) category else null)) {
                    context.toast(R.string.updating_category)
                }

                // It can be a very long operation, so we disable swipe refresh and show a toast.
                swipe_refresh.isRefreshing = false
            }
            .launchIn(scope)
    }

    fun onBind(category: Category) {
        this.category = category

        adapter.mode = if (controller.selectedMangas.isNotEmpty()) {
            SelectableAdapter.Mode.MULTI
        } else {
            SelectableAdapter.Mode.SINGLE
        }
        // SY -->
        adapter.isLongPressDragEnabled = adapter.canDrag()
        // SY <--

        // EXH -->
        supervisorScope = newScope()
        initialLoadHandle = controller.loaderManager.openProgressBar()
        // EXH <--

        subscriptions += controller.searchRelay
            .doOnNext { adapter.searchText = it }
            .skip(1)
            .debounce(500, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                // EXH -->
                supervisorScope.launch {
                    val handle = controller.loaderManager.openProgressBar()
                    try {
                        // EXH <--
                        adapter.performFilter(this)
                        // EXH -->
                    } finally {
                        controller.loaderManager.closeProgressBar(handle)
                    }
                }
                // EXH <--
            }

        subscriptions += controller.libraryMangaRelay
            .subscribe {
                // EXH -->
                supervisorScope.launch {
                    try {
                        // EXH <--
                        onNextLibraryManga(this, it)
                        // EXH -->
                    } finally {
                        controller.loaderManager.closeProgressBar(initialLoadHandle)
                    }
                }
                // EXH <--
            }

        subscriptions += controller.selectionRelay
            .subscribe { onSelectionChanged(it) }

        subscriptions += controller.selectAllRelay
            .filter { it == category.id }
            .subscribe {
                adapter.currentItems.forEach { item ->
                    controller.setSelection(item.manga, true)
                }
                controller.invalidateActionMode()
            }

        subscriptions += controller.selectInverseRelay
            .filter { it == category.id }
            .subscribe {
                adapter.currentItems.forEach { item ->
                    controller.toggleSelection(item.manga)
                }
                controller.invalidateActionMode()
            }
    }

    fun onRecycle() {
        // SY -->
        runBlocking { adapter.setItems(this, emptyList()) }
        // SY <--
        adapter.clearSelection()
        unsubscribe()
    }

    fun unsubscribe() {
        subscriptions.clear()
        // EXH -->
        supervisorScope.cancel()
        controller.loaderManager.closeProgressBar(initialLoadHandle)
        // EXH <--
    }

    /**
     * Subscribe to [LibraryMangaEvent]. When an event is received, it updates the content of the
     * adapter.
     *
     * @param event the event received.
     */
    private suspend fun onNextLibraryManga(cScope: CoroutineScope, event: LibraryMangaEvent) {
        // Get the manga list for this category.
        // SY -->
        val sortingMode = preferences.librarySortingMode().get()
        adapter.isLongPressDragEnabled = adapter.canDrag()
        var mangaForCategory = event.getMangaForCategory(category).orEmpty()
        if (sortingMode == LibrarySort.DRAG_AND_DROP) {
            if (category.id == 0) {
                category.mangaOrder = preferences.defaultMangaOrder().get()
                    .split("/")
                    .mapNotNull { it.toLongOrNull() }
            }
            mangaForCategory = mangaForCategory.sortedBy {
                category.mangaOrder.indexOf(it.manga.id)
            }
        }
        // SY <--
        // Update the category with its manga.
        // EXH -->
        adapter.setItems(cScope, mangaForCategory)
        // EXH <--

        if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            controller.selectedMangas.forEach { manga ->
                val position = adapter.indexOf(manga)
                if (position != -1 && !adapter.isSelected(position)) {
                    adapter.toggleSelection(position)
                    (recycler.findViewHolderForItemId(manga.id!!) as? LibraryHolder)?.toggleActivation()
                }
            }
        }
    }

    /**
     * Subscribe to [LibrarySelectionEvent]. When an event is received, it updates the selection
     * depending on the type of event received.
     *
     * @param event the selection event received.
     */
    private fun onSelectionChanged(event: LibrarySelectionEvent) {
        when (event) {
            is LibrarySelectionEvent.Selected -> {
                if (adapter.mode != SelectableAdapter.Mode.MULTI) {
                    adapter.mode = SelectableAdapter.Mode.MULTI
                    // SY -->
                    adapter.isLongPressDragEnabled = adapter.canDrag()
                    // SY <--
                }
                findAndToggleSelection(event.manga)
            }
            is LibrarySelectionEvent.Unselected -> {
                findAndToggleSelection(event.manga)
                if (adapter.indexOf(event.manga) != -1) lastClickPosition = -1
                if (controller.selectedMangas.isEmpty()) {
                    adapter.mode = SelectableAdapter.Mode.SINGLE
                    // SY -->
                    adapter.isLongPressDragEnabled = adapter.canDrag()
                    // SY <--
                }
            }
            is LibrarySelectionEvent.Cleared -> {
                adapter.mode = SelectableAdapter.Mode.SINGLE
                adapter.clearSelection()
                lastClickPosition = -1
                // SY -->
                adapter.isLongPressDragEnabled = adapter.canDrag()
                // SY <--
            }
        }
    }

    /**
     * Toggles the selection for the given manga and updates the view if needed.
     *
     * @param manga the manga to toggle.
     */
    private fun findAndToggleSelection(manga: Manga) {
        val position = adapter.indexOf(manga)
        if (position != -1) {
            adapter.toggleSelection(position)
            (recycler.findViewHolderForItemId(manga.id!!) as? LibraryHolder)?.toggleActivation()
        }
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        // If the action mode is created and the position is valid, toggle the selection.
        val item = adapter.getItem(position) ?: return false
        return if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            lastClickPosition = position
            toggleSelection(position)
            true
        } else {
            openManga(item.manga)
            false
        }
    }

    /**
     * Called when a manga is long clicked.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        controller.createActionModeIfNeeded()
        // SY -->
        adapter.isLongPressDragEnabled = adapter.canDrag()
        // SY <--
        when {
            lastClickPosition == -1 -> setSelection(position)
            lastClickPosition > position ->
                for (i in position until lastClickPosition)
                    setSelection(i)
            lastClickPosition < position ->
                for (i in lastClickPosition + 1..position)
                    setSelection(i)
            else -> setSelection(position)
        }
        lastClickPosition = position
    }
    // SY -->
    override fun onItemReleased(position: Int) {
        return
    }

    override fun shouldMoveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (adapter.isSelected(fromPosition)) toggleSelection(fromPosition)
        return true
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        val position = viewHolder?.bindingAdapterPosition ?: return
        if (actionState == 2) {
            onItemLongClick(position)
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        controller.invalidateActionMode()
        val mangaIds = adapter.currentItems.mapNotNull { it.manga.id }
        category.mangaOrder = mangaIds
        if (category.id == 0) {
            preferences.defaultMangaOrder().set(mangaIds.joinToString("/"))
        } else {
            db.insertCategory(category).asRxObservable().subscribe()
        }
        if (preferences.librarySortingMode().get() != LibrarySort.DRAG_AND_DROP) {
            preferences.librarySortingAscending().set(true)
            preferences.librarySortingMode().set(LibrarySort.DRAG_AND_DROP)
            controller.refreshSort()
        }
    }
    // SY <--

    /**
     * Opens a manga.
     *
     * @param manga the manga to open.
     */
    private fun openManga(manga: Manga) {
        controller.openManga(manga)
    }

    /**
     * Tells the presenter to toggle the selection for the given position.
     *
     * @param position the position to toggle.
     */
    private fun toggleSelection(position: Int) {
        val item = adapter.getItem(position) ?: return

        controller.setSelection(item.manga, !adapter.isSelected(position))
        controller.invalidateActionMode()
    }

    /**
     * Tells the presenter to set the selection for the given position.
     *
     * @param position the position to toggle.
     */
    private fun setSelection(position: Int) {
        val item = adapter.getItem(position) ?: return

        controller.setSelection(item.manga, true)
        controller.invalidateActionMode()
    }
}
