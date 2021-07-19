package eu.kanade.tachiyomi.ui.category.sources

import android.app.Dialog
import android.os.Bundle
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.widget.materialdialogs.setTextInput

/**
 * Dialog to create a new category for the library.
 */
class SourceCategoryCreateDialog<T>(bundle: Bundle? = null) : DialogController(bundle)
        where T : Controller, T : SourceCategoryCreateDialog.Listener {

    /**
     * Name of the new category. Value updated with each input from the user.
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
            .setTitle(R.string.action_add_category)
            .setNegativeButton(android.R.string.cancel, null)
            .setTextInput(
                hint = resources?.getString(R.string.name),
                prefill = currentName
            ) { input ->
                currentName = input
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                (targetController as? Listener)?.createCategory(currentName)
            }
            .create()
    }

    interface Listener {
        fun createCategory(name: String)
    }
}
