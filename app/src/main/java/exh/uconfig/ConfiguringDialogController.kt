package exh.uconfig

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogE
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class ConfiguringDialogController : DialogController() {
    private var materialDialog: AlertDialog? = null
    val scope = MainScope()

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        if (savedViewState == null) {
            scope.launchIO {
                try {
                    EHConfigurator(activity!!).configureAll()
                    launchUI {
                        activity?.toast(R.string.eh_settings_successfully_uploaded)
                    }
                } catch (e: Exception) {
                    launchUI {
                        activity?.let {
                            MaterialAlertDialogBuilder(it)
                                .setTitle(R.string.eh_settings_configuration_failed)
                                .setMessage(it.getString(R.string.eh_settings_configuration_failed_message, e.message))
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                    this@ConfiguringDialogController.xLogE("Configuration error!", e)
                }
                launchUI {
                    finish()
                }
            }
        }

        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.eh_settings_uploading_to_server)
            .setMessage(R.string.eh_settings_uploading_to_server_message)
            .setCancelable(false)
            .create()
            .also {
                materialDialog = it
            }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        materialDialog = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        finish()
    }

    fun finish() {
        router.popController(this)
    }
}
