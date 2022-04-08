package exh.widget.preference

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.dd.processbutton.iml.ActionProcessButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.PrefSiteLoginTwoFactorAuthBinding
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.LoginSource
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogW
import exh.source.getMainSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MangadexLoginDialog(bundle: Bundle? = null) : DialogController(bundle) {

    val source = Injekt.get<SourceManager>().get(args.getLong("key", 0))?.getMainSource() as LoginSource

    val preferences: PreferencesHelper by injectLazy()

    val scope = CoroutineScope(Job() + Dispatchers.Main)

    lateinit var binding: PrefSiteLoginTwoFactorAuthBinding

    constructor(source: LoginSource) : this(
        bundleOf(
            "key" to source.id,
        ),
    )

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = PrefSiteLoginTwoFactorAuthBinding.inflate(LayoutInflater.from(activity!!))
        onViewCreated()
        return MaterialAlertDialogBuilder(activity!!)
            .setView(binding.root)
            .create()
    }

    fun onViewCreated() {
        binding.login.setMode(ActionProcessButton.Mode.ENDLESS)
        binding.login.setOnClickListener { checkLogin() }

        setCredentialsOnView()

        when (source.twoFactorAuth) {
            LoginSource.AuthSupport.REQUIRED -> {
                binding.twoFactorCheck.isVisible = false
                binding.twoFactorHolder.isVisible = true
            }
            LoginSource.AuthSupport.SUPPORTED -> {
                binding.twoFactorCheck.setOnCheckedChangeListener { _, isChecked ->
                    binding.twoFactorHolder.isVisible = isChecked
                }
            }
            LoginSource.AuthSupport.NOT_SUPPORTED -> {
                binding.twoFactorCheck.isVisible = false
                binding.twoFactorHolder.isVisible = false
            }
        }
    }

    private fun setCredentialsOnView() {
        binding.username.setText(source.getUsername())
        binding.password.setText(source.getPassword())
    }

    private fun checkLogin() {
        val username = binding.username.text?.toString()
        val password = binding.password.text?.toString()
        val twoFactor = binding.twoFactorEdit.text?.toString()
        if (username.isNullOrBlank() || password.isNullOrBlank() || (binding.twoFactorCheck.isChecked && twoFactor.isNullOrBlank())) {
            errorResult()
            launchUI {
                binding.root.context.toast(R.string.fields_cannot_be_blank)
            }
            return
        }

        binding.login.progress = 1

        dialog?.setCancelable(false)
        dialog?.setCanceledOnTouchOutside(false)

        scope.launch {
            try {
                val result = source.login(
                    username,
                    password,
                    twoFactor.toString(),
                )
                if (result) {
                    dialog?.dismiss()
                    withUIContext {
                        binding.root.context.toast(R.string.login_success)
                    }
                } else {
                    errorResult()
                }
            } catch (error: Exception) {
                errorResult()
                xLogW("Login to Mangadex error", error)
                error.message?.let { launchUI { binding.root.context.toast(it) } }
            }
        }
    }

    private fun errorResult() {
        with(binding) {
            dialog?.setCancelable(true)
            dialog?.setCanceledOnTouchOutside(true)
            login.progress = -1
            login.setText(R.string.unknown_error)
        }
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (!type.isEnter) {
            onDialogClosed()
        }
    }

    private fun onDialogClosed() {
        scope.cancel()
        if (activity != null) {
            (activity as? Listener)?.siteLoginDialogClosed(source)
        } else {
            (targetController as? Listener)?.siteLoginDialogClosed(source)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    interface Listener {
        fun siteLoginDialogClosed(source: Source)
    }
}
