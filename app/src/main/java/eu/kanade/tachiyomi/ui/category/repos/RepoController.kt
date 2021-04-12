package eu.kanade.tachiyomi.ui.category.repos

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
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

/**
 * Controller to manage the categories for the users' library.
 */
class RepoController :
    NucleusController<CategoriesControllerBinding, RepoPresenter>(),
    FabController,
    ActionMode.Callback,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    RepoCreateDialog.Listener,
    UndoHelper.OnActionListener {

    /**
     * Object used to show ActionMode toolbar.
     */
    private var actionMode: ActionMode? = null

    /**
     * Adapter containing repo items.
     */
    private var adapter: RepoAdapter? = null

    private var actionFab: ExtendedFloatingActionButton? = null
    private var actionFabScrollListener: RecyclerView.OnScrollListener? = null

    /**
     * Undo helper used for restoring a deleted repo.
     */
    private var undoHelper: UndoHelper? = null

    /**
     * Creates the presenter for this controller. Not to be manually called.
     */
    override fun createPresenter() = RepoPresenter()

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle(): String? {
        return resources?.getString(R.string.action_edit_repos)
    }

    /**
     * Returns the view of this controller.
     *
     * @param inflater The layout inflater to create the view from XML.
     * @param container The parent view for this one.
     */
    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = CategoriesControllerBinding.inflate(inflater)
        binding.recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }
        return binding.root
    }

    /**
     * Called after view inflation. Used to initialize the view.
     *
     * @param view The view of this controller.
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        adapter = RepoAdapter(this@RepoController)
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
            RepoCreateDialog(this@RepoController).showDialog(router, null)
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
        // Manually call callback to delete repos if required
        undoHelper?.onDeleteConfirmed(Snackbar.Callback.DISMISS_EVENT_MANUAL)
        undoHelper = null
        actionMode = null
        adapter = null
        super.onDestroyView(view)
    }

    /**
     * Called from the presenter when the repos are updated.
     *
     * @param repos The new list of repos to display.
     */
    fun setRepos(repos: List<RepoItem>) {
        actionMode?.finish()
        adapter?.updateDataSet(repos)
        if (repos.isNotEmpty()) {
            binding.emptyView.hide()
            val selected = repos.filter { it.isSelected }
            if (selected.isNotEmpty()) {
                selected.forEach { onItemLongClick(repos.indexOf(it)) }
            }
        } else {
            binding.emptyView.show(R.string.information_empty_repos)
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
                    R.string.snack_repo_deleted,
                    R.string.action_undo,
                    3000
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
        presenter.deleteRepos(adapter.deletedItems.map { it.repo })
        undoHelper = null
    }

    /**
     * Creates a new repo with the given name.
     *
     * @param name The name of the new repo.
     */
    override fun createRepo(name: String) {
        presenter.createRepo(name)
    }

    /**
     * Called from the presenter when a repo already exists.
     */
    fun onRepoExistsError() {
        activity?.toast(R.string.error_repo_exists)
    }

    /**
     * Called from the presenter when a invalid repo is made
     */
    fun onRepoInvalidNameError() {
        activity?.toast(R.string.invalid_repo_name)
    }
}
