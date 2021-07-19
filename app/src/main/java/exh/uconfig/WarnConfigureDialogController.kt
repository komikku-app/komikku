package exh.uconfig

import android.app.Dialog
import android.os.Bundle
import com.bluelinelabs.conductor.Router
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class WarnConfigureDialogController : DialogController() {
    private val prefs: PreferencesHelper by injectLazy()
    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.settings_profile_note)
            .setMessage(R.string.settings_profile_note_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.exhShowSettingsUploadWarning().set(false)
                ConfiguringDialogController().showDialog(router)
            }
            .setCancelable(false)
            .create()
    }

    companion object {
        fun uploadSettings(router: Router) {
            if (Injekt.get<PreferencesHelper>().exhShowSettingsUploadWarning().get()) {
                WarnConfigureDialogController().showDialog(router)
            } else {
                ConfiguringDialogController().showDialog(router)
            }
        }
    }
}
