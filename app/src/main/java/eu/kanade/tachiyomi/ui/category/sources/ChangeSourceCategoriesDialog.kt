package eu.kanade.tachiyomi.ui.category.sources

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.controller.DialogController

class ChangeSourceCategoriesDialog<T>(bundle: Bundle? = null) :
    DialogController(bundle) where T : Controller, T : ChangeSourceCategoriesDialog.Listener {

    private var source: Source? = null

    private var categories = emptyList<String>()

    private var preselected = emptyArray<Int>()

    constructor(
        target: T,
        source: Source,
        categories: List<String>,
        preselected: Array<Int>
    ) : this() {
        this.source = source
        this.categories = categories
        this.preselected = preselected
        targetController = target
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialDialog(activity!!)
            .title(R.string.action_move_category)
            .listItemsMultiChoice(
                items = categories,
                initialSelection = preselected.toIntArray(),
                allowEmptySelection = true
            ) { _, selections, _ ->
                val newCategories = selections.map { categories[it] }
                (targetController as? Listener)?.updateCategoriesForSource(source!!, newCategories)
            }
            .positiveButton(android.R.string.ok)
            .negativeButton(android.R.string.cancel)
    }

    interface Listener {
        fun updateCategoriesForSource(source: Source, categories: List<String>)
    }
}
