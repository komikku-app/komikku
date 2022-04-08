package eu.kanade.tachiyomi.ui.browse.migration

import android.app.Dialog
import android.os.Bundle
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListController

class MigrationMangaDialog<T>(bundle: Bundle? = null) : DialogController(bundle)
    where T : Controller {

    var copy = false
    var mangaSet = 0
    var mangaSkipped = 0
    constructor(target: T, copy: Boolean, mangaSet: Int, mangaSkipped: Int) : this() {
        targetController = target
        this.copy = copy
        this.mangaSet = mangaSet
        this.mangaSkipped = mangaSkipped
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val confirmRes = if (copy) R.plurals.copy_manga else R.plurals.migrate_manga
        val confirmString = applicationContext?.resources?.getQuantityString(
            confirmRes,
            mangaSet,
            mangaSet,
            (
                if (mangaSkipped > 0) " " + applicationContext?.getString(R.string.skipping_, mangaSkipped)
                else ""
                ),
        ).orEmpty()
        return MaterialAlertDialogBuilder(activity!!)
            .setMessage(confirmString)
            .setPositiveButton(if (copy) R.string.copy else R.string.migrate) { _, _ ->
                if (copy) {
                    (targetController as? MigrationListController)?.copyMangas()
                } else {
                    (targetController as? MigrationListController)?.migrateMangas()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}
