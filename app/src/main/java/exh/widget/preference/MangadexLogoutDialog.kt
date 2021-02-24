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
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.system.toast
import exh.source.getMainSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
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
                    supervisorScope {
                        if (source != null) {
                            var exception: Exception? = null
                            val loggedOut = try {
                                withIOContext { source.logout() }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                exception = e
                                false
                            }

                            if (loggedOut) {
                                trackManager.mdList.logout()
                                activity?.toast(R.string.logout_success)
                                (targetController as? Listener)?.siteLogoutDialogClosed(source)
                            } else {
                                launchUI {
                                    if (exception != null) {
                                        activity?.toast(exception.message)
                                    } else {
                                        activity?.toast(R.string.unknown_error)
                                    }
                                }
                            }
                        } else launchUI { activity?.toast("Mangadex not enabled") }
                    }
                }
            }
            .negativeButton(android.R.string.cancel)
    }

    interface Listener {
        fun siteLogoutDialogClosed(source: Source)
    }
}
