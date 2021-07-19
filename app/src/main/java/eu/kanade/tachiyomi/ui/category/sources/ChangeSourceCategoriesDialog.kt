package eu.kanade.tachiyomi.ui.category.sources

import android.app.Dialog
import android.os.Bundle
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.controller.DialogController

class ChangeSourceCategoriesDialog<T>(bundle: Bundle? = null) :
    DialogController(bundle) where T : Controller, T : ChangeSourceCategoriesDialog.Listener {

    private var source: Source? = null

    private var categories = emptyArray<String>()

    private var selection = booleanArrayOf()

    constructor(
        target: T,
        source: Source,
        categories: Array<String>,
        selection: BooleanArray
    ) : this() {
        this.source = source
        this.categories = categories
        this.selection = selection
        targetController = target
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.action_move_category)
            .setMultiChoiceItems(
                categories,
                selection
            ) { _, which, selected ->
                selection[which] = selected
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newCategories = categories.filterIndexed { index, s ->
                    selection[index]
                }
                (targetController as? Listener)?.updateCategoriesForSource(source!!, newCategories)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    interface Listener {
        fun updateCategoriesForSource(source: Source, categories: List<String>)
    }
}
