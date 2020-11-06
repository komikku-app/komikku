package exh.widget.preference

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.internal.main.DialogLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.databinding.PrefSiteLoginTwoFactorAuthBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.preference.LoginDialogPreference
import exh.source.getMainSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangadexLoginDialog(bundle: Bundle? = null) : LoginDialogPreference(bundle = bundle) {

    val source = Injekt.get<SourceManager>().get(args.getLong("key", 0))?.getMainSource() as? MangaDex

    val service = Injekt.get<TrackManager>().mdList

    val scope = CoroutineScope(Job() + Dispatchers.Main)

    var binding: PrefSiteLoginTwoFactorAuthBinding? = null

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
        (v as? DialogLayout?)?.contentLayout?.customView?.let { binding = PrefSiteLoginTwoFactorAuthBinding.bind(it) }
        binding?.apply {
            twoFactorCheck.setOnCheckedChangeListener { _, isChecked ->
                twoFactorHolder.isVisible = isChecked
            }
        }
    }

    override fun setCredentialsOnView(view: View) {
        binding?.username?.setText(service.getUsername())
        binding?.password?.setText(service.getPassword())
    }

    override fun checkLogin() {
        binding?.apply {
            if (username.text.isNullOrBlank() || password.text.isNullOrBlank() || (twoFactorCheck.isChecked && twoFactorEdit.text.isNullOrBlank())) {
                errorResult()
                root.context.toast(R.string.fields_cannot_be_blank)
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
                        twoFactorEdit.text.toString()
                    ) ?: false
                    if (result) {
                        dialog?.dismiss()
                        preferences.setTrackCredentials(Injekt.get<TrackManager>().mdList, username.toString(), password.toString())
                        root.context.toast(R.string.login_success)
                    } else {
                        errorResult()
                    }
                } catch (error: Exception) {
                    errorResult()
                    error.message?.let { root.context.toast(it) }
                }
            }
        }
    }

    private fun errorResult() {
        binding?.apply {
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
