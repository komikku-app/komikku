package eu.kanade.tachiyomi.ui.main

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import exh.syDebugVersion
import it.gmariotti.changelibs.library.view.ChangeLogRecyclerView

class WhatsNewDialogController : DialogController() {

    @Suppress("DEPRECATION")
    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val activity = activity!!
        val view = WhatsNewRecyclerView(activity)
        return MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.whats_new)
            .setView(view)
            .setPositiveButton(android.R.string.cancel, null)
            .create()
    }

    class WhatsNewRecyclerView(context: Context) : ChangeLogRecyclerView(context) {
        override fun initAttrs(attrs: AttributeSet?, defStyle: Int) {
            mRowLayoutId = R.layout.changelog_row_layout
            mRowHeaderLayoutId = R.layout.changelog_header_layout
            mChangeLogFileResourceId = if (BuildConfig.DEBUG /* SY --> */ || syDebugVersion != "0"/* SY <-- */) R.raw.changelog_debug else R.raw.changelog_release
        }
    }
}
