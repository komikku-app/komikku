package exh.widget.preference

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.lang.launchNow
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import exh.source.getMainSource
import kotlinx.coroutines.supervisorScope
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MangadexLogoutDialog(bundle: Bundle? = null) : DialogController(bundle) {

    val source = Injekt.get<SourceManager>().get(args.getLong("key", 0))?.getMainSource() as? LoginSource

    val trackManager: TrackManager by injectLazy()

    constructor(source: Source) : this(
        bundleOf(
            "key" to source.id
        )
    )

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.logout)
            .setPositiveButton(R.string.logout) { _, _ ->
                launchNow {
                    supervisorScope {
                        if (source != null) {
                            val loggedOut = withIOContext {
                                source.logout()
                            }

                            if (loggedOut) {
                                withUIContext {
                                    activity?.toast(R.string.logout_success)
                                }
                                (targetController as? Listener)?.siteLogoutDialogClosed(source)
                            } else {
                                withUIContext {
                                    activity?.toast(R.string.unknown_error)
                                }
                            }
                        } else withUIContext { activity?.toast("Mangadex not enabled") }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    interface Listener {
        fun siteLogoutDialogClosed(source: Source)
    }
}
