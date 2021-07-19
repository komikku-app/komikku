package eu.kanade.tachiyomi.ui.category.repos

import android.app.Dialog
import android.os.Bundle
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.widget.materialdialogs.setTextInput

/**
 * Dialog to create a new repo for the library.
 */
class RepoCreateDialog<T>(bundle: Bundle? = null) : DialogController(bundle)
        where T : Controller, T : RepoCreateDialog.Listener {

    /**
     * Name of the new repo. Value updated with each input from the user.
     */
    private var currentName = ""

    constructor(target: T) : this() {
        targetController = target
    }

    /**
     * Called when creating the dialog for this controller.
     *
     * @param savedViewState The saved state of this dialog.
     * @return a new dialog instance.
     */
    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.action_add_repo)
            .setMessage(R.string.action_add_repo_message)
            .setTextInput(
                hint = resources?.getString(R.string.name),
                prefill = currentName
            ) { input ->
                currentName = input
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                (targetController as? Listener)?.createRepo(currentName)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    interface Listener {
        fun createRepo(name: String)
    }
}
