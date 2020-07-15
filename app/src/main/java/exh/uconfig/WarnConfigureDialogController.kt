package exh.uconfig

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.Router
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class WarnConfigureDialogController : DialogController() {
    private val prefs: PreferencesHelper by injectLazy()
    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialDialog(activity!!)
            .title(R.string.settings_profile_note)
            .message(R.string.settings_profile_note_message)
            .positiveButton(android.R.string.ok) {
                prefs.eh_showSettingsUploadWarning().set(false)
                ConfiguringDialogController().showDialog(router)
            }
            .cancelable(false)
    }

    companion object {
        fun uploadSettings(router: Router) {
            if (Injekt.get<PreferencesHelper>().eh_showSettingsUploadWarning().get()) {
                WarnConfigureDialogController().showDialog(router)
            } else {
                ConfiguringDialogController().showDialog(router)
            }
        }
    }
}
