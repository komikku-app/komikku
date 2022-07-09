package eu.kanade.tachiyomi.ui.category.biometric

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import dev.chrisbanes.insetter.applyInsetter
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.helpers.UndoHelper
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CategoriesControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.shrinkOnScroll
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Controller to manage the lock times for the biometric lock.
 */
class BiometricTimesController :
    NucleusController<CategoriesControllerBinding, BiometricTimesPresenter>(),
    FabController,
    ActionMode.Callback,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    UndoHelper.OnActionListener {

    /**
     * Object used to show ActionMode toolbar.
     */
    private var actionMode: ActionMode? = null

    /**
     * Adapter containing biometric lock time items.
     */
    private var adapter: BiometricTimesAdapter? = null

    private var actionFab: ExtendedFloatingActionButton? = null
    private var actionFabScrollListener: RecyclerView.OnScrollListener? = null

    /**
     * Undo helper used for restoring a deleted lock time.
     */
    private var undoHelper: UndoHelper? = null

    /**
     * Creates the presenter for this controller. Not to be manually called.
     */
    override fun createPresenter() = BiometricTimesPresenter()

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle(): String? {
        return resources?.getString(R.string.biometric_lock_times)
    }

    override fun createBinding(inflater: LayoutInflater) = CategoriesControllerBinding.inflate(inflater)

    /**
     * Called after view inflation. Used to initialize the view.
     *
     * @param view The view of this controller.
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        adapter = BiometricTimesAdapter(this@BiometricTimesController)
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.adapter = adapter
        adapter?.isPermanentDelete = false

        actionFabScrollListener = actionFab?.shrinkOnScroll(binding.recycler)
    }

    override fun configureFab(fab: ExtendedFloatingActionButton) {
        actionFab = fab
        fab.setText(R.string.action_add)
        fab.setIconResource(R.drawable.ic_add_24dp)
        fab.setOnClickListener {
            showTimePicker()
        }
    }

    override fun cleanupFab(fab: ExtendedFloatingActionButton) {
        fab.setOnClickListener(null)
        actionFabScrollListener?.let { binding.recycler.removeOnScrollListener(it) }
        actionFab = null
    }

    /**
     * Called when the view is being destroyed. Used to release references and remove callbacks.
     *
     * @param view The view of this controller.
     */
    override fun onDestroyView(view: View) {
        // Manually call callback to delete lock times if required
        undoHelper?.onDeleteConfirmed(Snackbar.Callback.DISMISS_EVENT_MANUAL)
        undoHelper = null
        actionMode = null
        adapter = null
        super.onDestroyView(view)
    }

    /**
     * Called from the presenter when the biometric lock times are updated.
     *
     * @param biometricTimeItems The new list of lock times to display.
     */
    fun setBiometricTimeItems(biometricTimeItems: List<BiometricTimesItem>) {
        actionMode?.finish()
        adapter?.updateDataSet(biometricTimeItems)
        if (biometricTimeItems.isNotEmpty()) {
            binding.emptyView.hide()
            val selected = biometricTimeItems.filter { it.isSelected }
            if (selected.isNotEmpty()) {
                selected.forEach { onItemLongClick(biometricTimeItems.indexOf(it)) }
            }
        } else {
            binding.emptyView.show(R.string.biometric_lock_times_empty)
        }
    }

    /**
     * Called when action mode is first created. The menu supplied will be used to generate action
     * buttons for the action mode.
     *
     * @param mode ActionMode being created.
     * @param menu Menu used to populate action buttons.
     * @return true if the action mode should be created, false if entering this mode should be
     *              aborted.
     */
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        // Inflate menu.
        mode.menuInflater.inflate(R.menu.category_selection, menu)
        // Enable adapter multi selection.
        adapter?.mode = SelectableAdapter.Mode.MULTI
        return true
    }

    /**
     * Called to refresh an action mode's action menu whenever it is invalidated.
     *
     * @param mode ActionMode being prepared.
     * @param menu Menu used to populate action buttons.
     * @return true if the menu or action mode was updated, false otherwise.
     */
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val adapter = adapter ?: return false
        val count = adapter.selectedItemCount
        mode.title = count.toString()

        // Show edit button only when one item is selected
        val editItem = mode.menu.findItem(R.id.action_edit)
        editItem.isVisible = false
        return true
    }

    /**
     * Called to report a user click on an action button.
     *
     * @param mode The current ActionMode.
     * @param item The item that was clicked.
     * @return true if this callback handled the event, false if the standard MenuItem invocation
     *              should continue.
     */
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val adapter = adapter ?: return false

        when (item.itemId) {
            R.id.action_delete -> {
                undoHelper = UndoHelper(adapter, this)
                undoHelper?.start(
                    adapter.selectedPositions,
                    (activity as? MainActivity)?.binding?.rootCoordinator!!,
                    R.string.biometric_lock_time_deleted_snack,
                    R.string.action_undo,
                    3000,
                )

                mode.finish()
            }
            else -> return false
        }
        return true
    }

    /**
     * Called when an action mode is about to be exited and destroyed.
     *
     * @param mode The current ActionMode being destroyed.
     */
    override fun onDestroyActionMode(mode: ActionMode) {
        // Reset adapter to single selection
        adapter?.mode = SelectableAdapter.Mode.IDLE
        adapter?.clearSelection()
        actionMode = null
    }

    /**
     * Called when an item in the list is clicked.
     *
     * @param position The position of the clicked item.
     * @return true if this click should enable selection mode.
     */
    override fun onItemClick(view: View, position: Int): Boolean {
        // Check if action mode is initialized and selected item exist.
        return if (actionMode != null && position != RecyclerView.NO_POSITION) {
            toggleSelection(position)
            true
        } else {
            false
        }
    }

    /**
     * Called when an item in the list is long clicked.
     *
     * @param position The position of the clicked item.
     */
    override fun onItemLongClick(position: Int) {
        val activity = activity as? AppCompatActivity ?: return

        // Check if action mode is initialized.
        if (actionMode == null) {
            // Initialize action mode
            actionMode = activity.startSupportActionMode(this)
        }

        // Set item as selected
        toggleSelection(position)
    }

    /**
     * Toggle the selection state of an item.
     * If the item was the last one in the selection and is unselected, the ActionMode is finished.
     *
     * @param position The position of the item to toggle.
     */
    private fun toggleSelection(position: Int) {
        val adapter = adapter ?: return

        // Mark the position selected
        adapter.toggleSelection(position)

        if (adapter.selectedItemCount == 0) {
            actionMode?.finish()
        } else {
            actionMode?.invalidate()
        }
    }

    /**
     * Called when the undo action is clicked in the snackbar.
     *
     * @param action The action performed.
     */
    override fun onActionCanceled(action: Int, positions: MutableList<Int>?) {
        adapter?.restoreDeletedItems()
        undoHelper = null
    }

    /**
     * Called when the time to restore the items expires.
     *
     * @param action The action performed.
     * @param event The event that triggered the action
     */
    override fun onActionConfirmed(action: Int, event: Int) {
        val adapter = adapter ?: return
        presenter.deleteTimeRanges(adapter.deletedItems.map { it.timeRange })
        undoHelper = null
    }

    /**
     * Called from the presenter when a time range conflicts with another.
     */
    fun onTimeRangeConflictsError() {
        activity?.toast(R.string.biometric_lock_time_conflicts)
    }

    fun showTimePicker(startTime: Duration? = null) {
        val picker = MaterialTimePicker.Builder()
            .setTitleText(if (startTime == null) R.string.biometric_lock_start_time else R.string.biometric_lock_end_time)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .build()
        picker.addOnPositiveButtonClickListener {
            val timeRange = picker.hour.hours + picker.minute.minutes
            if (startTime != null) {
                presenter.createTimeRange(TimeRange(startTime, timeRange))
            } else {
                showTimePicker(timeRange)
            }
        }
        picker.show((activity as MainActivity).supportFragmentManager, null)
    }
}
