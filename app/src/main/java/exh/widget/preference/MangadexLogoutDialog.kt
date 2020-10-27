package exh.widget.preference

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.lang.launchNow
import eu.kanade.tachiyomi.util.system.toast
import exh.source.getMainSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MangadexLogoutDialog(bundle: Bundle? = null) : DialogController(bundle) {

    val source = Injekt.get<SourceManager>().get(args.getLong("key", 0))?.getMainSource() as? MangaDex

    val trackManager: TrackManager by injectLazy()

    constructor(source: Source) : this(
        bundleOf(
            "key" to source.id
        )
    )

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialDialog(activity!!)
            .title(R.string.logout)
            .positiveButton(R.string.logout) {
                launchNow {
                    source?.let { source ->
                        val loggedOut = withContext(Dispatchers.IO) { source.logout() }

                        if (loggedOut) {
                            trackManager.mdList.logout()
                            activity?.toast(R.string.logout_success)
                            (targetController as? Listener)?.siteLogoutDialogClosed(source)
                        } else {
                            activity?.toast(R.string.unknown_error)
                        }
                    } ?: activity!!.toast("Mangadex not enabled")
                }
            }
            .negativeButton(android.R.string.cancel)
    }

    interface Listener {
        fun siteLogoutDialogClosed(source: Source)
    }
}
