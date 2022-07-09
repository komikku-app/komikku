package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.chrisbanes.insetter.applyInsetter
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.domain.category.interactor.UpdateCategory
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.domain.category.model.toDbCategory
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.toDomainManga
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.LibraryCategoryBinding
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.lang.plusAssign
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.onAnimationsFinished
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import exh.ui.LoadingHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import reactivecircus.flowbinding.recyclerview.scrollStateChanges
import reactivecircus.flowbinding.swiperefreshlayout.refreshes
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription
import uy.kohesive.injekt.injectLazy
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/**
 * Fragment containing the library manga for a certain category.
 */
class LibraryCategoryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    // SY -->
    FlexibleAdapter.OnItemMoveListener {
    // SY <--

    private val scope = MainScope()

    private val preferences: PreferencesHelper by injectLazy()

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
    private lateinit var recycler: AutofitRecyclerView

    /**
     * Adapter to hold the manga in this category.
     */
    private lateinit var adapter: LibraryCategoryAdapter

    /**
     * Subscriptions while the view is bound.
     */
    private var subscriptions = CompositeSubscription()

    private var lastClickPositionStack = ArrayDeque(listOf(-1))

    // EXH -->
    private var initialLoadHandle: LoadingHandle? = null
    // EXH <--

    fun onCreate(controller: LibraryController, binding: LibraryCategoryBinding, viewType: Int) {
        this.controller = controller

        recycler = if (viewType == LibraryAdapter.LIST_DISPLAY_MODE) {
            (binding.swipeRefresh.inflate(R.layout.library_list_recycler) as AutofitRecyclerView).apply {
                spanCount = 1
            }
        } else {
            (binding.swipeRefresh.inflate(R.layout.library_grid_recycler) as AutofitRecyclerView).apply {
                spanCount = controller.mangaPerRow
            }
        }

        recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        adapter = LibraryCategoryAdapter(this, controller)

        recycler.setHasFixedSize(true)
        recycler.adapter = adapter
        binding.swipeRefresh.addView(recycler)
        adapter.fastScroller = binding.fastScroller

        recycler.scrollStateChanges()
            .onEach {
                // Disable swipe refresh when view is not at the top
                val firstPos = (recycler.layoutManager as LinearLayoutManager)
                    .findFirstCompletelyVisibleItemPosition()
                binding.swipeRefresh.isEnabled = firstPos <= 0
            }
            .launchIn(scope)

        recycler.onAnimationsFinished {
            (controller.activity as? MainActivity)?.ready = true
        }

        // Double the distance required to trigger sync
        binding.swipeRefresh.setDistanceToTriggerSync((2 * 64 * resources.displayMetrics.density).toInt())
        binding.swipeRefresh.refreshes()
            .onEach {
                // SY -->
                if (LibraryUpdateService.start(context, if (controller.presenter.groupType == LibraryGroup.BY_DEFAULT) category else null, group = controller.presenter.groupType, groupExtra = getGroupExtra())) {
                    context.toast(
                        when {
                            controller.presenter.groupType == LibraryGroup.BY_DEFAULT ||
                                (preferences.groupLibraryUpdateType().get() == PreferenceValues.GroupLibraryMode.ALL) -> R.string.updating_category
                            (
                                controller.presenter.groupType == LibraryGroup.UNGROUPED &&
                                    preferences.groupLibraryUpdateType().get() == PreferenceValues.GroupLibraryMode.ALL_BUT_UNGROUPED
                                ) ||
                                preferences.groupLibraryUpdateType().get() == PreferenceValues.GroupLibraryMode.GLOBAL -> R.string.updating_library
                            else -> R.string.updating_category
                        },
                    )
                }
                // SY <--

                // It can be a very long operation, so we disable swipe refresh and show a toast.
                binding.swipeRefresh.isRefreshing = false
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
        initialLoadHandle = controller.loaderManager.openProgressBar()
        // EXH <--

        subscriptions += controller.searchRelay
            .doOnNext { adapter.searchText = it }
            .skip(1)
            .debounce(500, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                // EXH -->
                scope.launch {
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
                scope.launch {
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
                    controller.setSelection(item.manga.toDomainManga()!!, true)
                }
                controller.invalidateActionMode()
            }

        subscriptions += controller.selectInverseRelay
            .filter { it == category.id }
            .subscribe {
                adapter.currentItems.forEach { item ->
                    controller.toggleSelection(item.manga.toDomainManga()!!)
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

    fun onDestroy() {
        unsubscribe()
        scope.cancel()
        // SY -->
        controller.loaderManager.closeProgressBar(initialLoadHandle)
        // SY <--
    }

    private fun unsubscribe() {
        subscriptions.clear()
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
        adapter.isLongPressDragEnabled = adapter.canDrag()
        var mangaForCategory = event.getMangaForCategory(category).orEmpty()
        var mangaOrder = category.mangaOrder
        if (preferences.categorizedDisplaySettings().get() && category.id != 0L) {
            if (SortModeSetting.fromFlag(category.sortMode) == SortModeSetting.DRAG_AND_DROP) {
                mangaForCategory = mangaForCategory.sortedBy {
                    mangaOrder.indexOf(it.manga.id)
                }
            }
        } else if (preferences.librarySortingMode().get() == SortModeSetting.DRAG_AND_DROP) {
            if (category.id == 0L) {
                mangaOrder = preferences.defaultMangaOrder().get()
                    .split("/")
                    .mapNotNull { it.toLongOrNull() }
            }
            mangaForCategory = mangaForCategory.sortedBy {
                mangaOrder.indexOf(it.manga.id)
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
                    (recycler.findViewHolderForItemId(manga.id) as? LibraryHolder<*>)?.toggleActivation()
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

                with(adapter.indexOf(event.manga)) {
                    if (this != -1) lastClickPositionStack.remove(this)
                }

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

                lastClickPositionStack.clear()
                lastClickPositionStack.push(-1)
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
            (recycler.findViewHolderForItemId(manga.id) as? LibraryHolder<*>)?.toggleActivation()
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
            if (adapter.isSelected(position)) {
                lastClickPositionStack.remove(position)
            } else {
                lastClickPositionStack.push(position)
            }
            toggleSelection(position)
            true
        } else {
            openManga(item.manga.toDomainManga()!!)
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
        val lastClickPosition = lastClickPositionStack.peek()!!
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
        if (lastClickPosition != position) {
            lastClickPositionStack.remove(position)
            lastClickPositionStack.push(position)
        }
    }

    // SY -->
    private fun getGroupExtra() = when (controller.presenter.groupType) {
        LibraryGroup.BY_DEFAULT -> null
        LibraryGroup.BY_SOURCE, LibraryGroup.BY_STATUS, LibraryGroup.BY_TRACK_STATUS -> category.id.toString()
        else -> null
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

    private val updateCategory: UpdateCategory by injectLazy()

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        controller.invalidateActionMode()
        val mangaIds = adapter.currentItems.mapNotNull { it.manga.id }
        if (category.id == 0L) {
            preferences.defaultMangaOrder().set(mangaIds.joinToString("/"))
        } else {
            scope.launch {
                updateCategory.await(CategoryUpdate(category.id.toLong(), mangaOrder = mangaIds))
            }
        }
        if (preferences.categorizedDisplaySettings().get() && category.id != 0L) {
            if (SortModeSetting.fromFlag(category.sortMode) != SortModeSetting.DRAG_AND_DROP) {
                val dbCategory = category.toDbCategory()
                dbCategory.sortMode = SortModeSetting.DRAG_AND_DROP.flag.toInt()
                dbCategory.sortDirection = SortDirectionSetting.ASCENDING.flag.toInt()
                scope.launch {
                    updateCategory.await(
                        CategoryUpdate(
                            id = category.id,
                            flags = dbCategory.flags.toLong(),
                            mangaOrder = mangaIds,
                        ),
                    )
                }
            }
        } else if (preferences.librarySortingMode().get() != SortModeSetting.DRAG_AND_DROP) {
            preferences.librarySortingAscending().set(SortDirectionSetting.ASCENDING)
            preferences.librarySortingMode().set(SortModeSetting.DRAG_AND_DROP)
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

        controller.setSelection(item.manga.toDomainManga()!!, !adapter.isSelected(position))
        controller.invalidateActionMode()
    }

    /**
     * Tells the presenter to set the selection for the given position.
     *
     * @param position the position to toggle.
     */
    private fun setSelection(position: Int) {
        val item = adapter.getItem(position) ?: return

        controller.setSelection(item.manga.toDomainManga()!!, true)
        controller.invalidateActionMode()
    }
}
