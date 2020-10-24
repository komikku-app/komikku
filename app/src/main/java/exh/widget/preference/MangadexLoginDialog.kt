package exh.widget.preference

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.preference.LoginDialogPreference
import exh.md.utils.MdUtil
import kotlinx.android.synthetic.main.pref_site_login_two_factor_auth.view.login
import kotlinx.android.synthetic.main.pref_site_login_two_factor_auth.view.password
import kotlinx.android.synthetic.main.pref_site_login_two_factor_auth.view.two_factor_check
import kotlinx.android.synthetic.main.pref_site_login_two_factor_auth.view.two_factor_edit
import kotlinx.android.synthetic.main.pref_site_login_two_factor_auth.view.two_factor_holder
import kotlinx.android.synthetic.main.pref_site_login_two_factor_auth.view.username
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangadexLoginDialog(bundle: Bundle? = null) : LoginDialogPreference(bundle = bundle) {

    val source by lazy { MdUtil.getEnabledMangaDex() }

    val service = Injekt.get<TrackManager>().mdList

    val scope = CoroutineScope(Job() + Dispatchers.Main)

    constructor(source: MangaDex) : this(
        bundleOf(
            "key" to source.id
        )
    )

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val dialog = MaterialDialog(activity!!).apply {
            customView(R.layout.pref_site_login_two_factor_auth, scrollable = false)
        }

        onViewCreated(dialog.view)

        return dialog
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        v?.apply {
            two_factor_check?.setOnCheckedChangeListener { _, isChecked ->
                two_factor_holder.isVisible = isChecked
            }
        }
    }

    override fun setCredentialsOnView(view: View) = with(view) {
        username.setText(service.getUsername())
        password.setText(service.getPassword())
    }

    override fun checkLogin() {
        v?.apply {
            if (username.text.isNullOrBlank() || password.text.isNullOrBlank() || (two_factor_check.isChecked && two_factor_edit.text.isNullOrBlank())) {
                errorResult()
                context.toast(R.string.fields_cannot_be_blank)
                return
            }

            login.progress = 1

            dialog?.setCancelable(false)
            dialog?.setCanceledOnTouchOutside(false)

            scope.launch {
                try {
                    val result = source?.login(
                        username.text.toString(),
                        password.text.toString(),
                        two_factor_edit.text.toString()
                    ) ?: false
                    if (result) {
                        dialog?.dismiss()
                        preferences.setTrackCredentials(Injekt.get<TrackManager>().mdList, username.toString(), password.toString())
                        context.toast(R.string.login_success)
                    } else {
                        errorResult()
                    }
                } catch (error: Exception) {
                    errorResult()
                    error.message?.let { context.toast(it) }
                }
            }
        }
    }

    private fun errorResult() {
        v?.apply {
            dialog?.setCancelable(true)
            dialog?.setCanceledOnTouchOutside(true)
            login.progress = -1
            login.setText(R.string.unknown_error)
        }
    }

    override fun onDialogClosed() {
        super.onDialogClosed()
        if (activity != null) {
            (activity as? Listener)?.siteLoginDialogClosed(source!!)
        } else {
            (targetController as? Listener)?.siteLoginDialogClosed(source!!)
        }
    }

    interface Listener {
        fun siteLoginDialogClosed(source: Source)
    }
}
