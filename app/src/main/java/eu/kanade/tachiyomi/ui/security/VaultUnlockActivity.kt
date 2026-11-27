package eu.kanade.tachiyomi.ui.security

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import eu.kanade.presentation.vault.VaultUnlockScreen
import eu.kanade.tachiyomi.data.vault.VaultManager
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import eu.kanade.tachiyomi.util.view.setSecureScreen
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.vault.VaultSession
import tachiyomi.i18n.kmk.KMR

/**
 * Hosts the device credential prompt (PIN/pattern/password) for the private vault.
 *
 * On Android 11+ a device-credential-only [BiometricPrompt] is used, which explicitly does not
 * allow biometrics. Older Android versions can only use the system confirm-credentials screen,
 * which may offer biometrics as an OS-level fallback.
 */
class VaultUnlockActivity : BaseActivity() {

    private var hasDeviceLock by mutableStateOf(false)
    private var authenticated = false
    private var promptInFlight = false

    private val credentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        promptInFlight = false
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlocked()
        } else {
            VaultSession.onAuthenticationDismissed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSecureScreen(true)

        hasDeviceLock = isDeviceLockAvailable()

        onBackPressedDispatcher.addCallback(this) {
            VaultSession.onAuthenticationDismissed()
            // Leaving the entry prompt also leaves the vault session.
            VaultManager.lock(this@VaultUnlockActivity)
        }

        setComposeContent {
            VaultUnlockScreen(
                hasDeviceLock = hasDeviceLock,
                onUnlock = ::launchCredentialPrompt,
                onUseStandardLibrary = {
                    if (VaultManager.canSwitch(this@VaultUnlockActivity)) {
                        VaultManager.lock(this@VaultUnlockActivity)
                    } else {
                        toast(KMR.strings.private_vault_job_running)
                    }
                },
            )
        }

        if (savedInstanceState == null) {
            launchCredentialPrompt()
        }
    }

    private fun isDeviceLockAvailable(): Boolean {
        return VaultManager.isDeviceSecure(this)
    }

    private fun launchCredentialPrompt() {
        if (!hasDeviceLock) {
            VaultSession.onAuthenticationDismissed()
            return
        }
        if (promptInFlight) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            launchDeviceCredentialPrompt()
        } else {
            launchSystemCredentialPrompt()
        }
    }

    /**
     * Android 11+ device-credential-only prompt: no fingerprint/face option is offered.
     */
    private fun launchDeviceCredentialPrompt() {
        promptInFlight = true
        VaultSession.onAuthenticating()

        try {
            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        promptInFlight = false
                        onUnlocked()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        promptInFlight = false
                        if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) {
                            hasDeviceLock = false
                        }
                        VaultSession.onAuthenticationDismissed()
                    }
                },
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(stringResource(KMR.strings.private_vault))
                .setDescription(stringResource(KMR.strings.private_vault_unlock_hint))
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()

            prompt.authenticate(promptInfo)
        } catch (_: Exception) {
            promptInFlight = false
            hasDeviceLock = false
            VaultSession.onAuthenticationDismissed()
        }
    }

    /**
     * Android 10 and older cannot request device-credential-only from the biometric library, so
     * fall back to the system confirm-credentials screen.
     */
    private fun launchSystemCredentialPrompt() {
        val keyguardManager = getSystemService<KeyguardManager>()
        val intent = keyguardManager?.createConfirmDeviceCredentialIntent(
            stringResource(KMR.strings.private_vault),
            stringResource(KMR.strings.private_vault_unlock_hint),
        )

        if (intent == null) {
            VaultSession.onAuthenticationDismissed()
            return
        }

        promptInFlight = true
        VaultSession.onAuthenticating()
        credentialLauncher.launch(intent)
    }

    private fun onUnlocked() {
        authenticated = true
        VaultSession.onAuthenticated()
        SecureActivityDelegate.unlock()
        finish()
    }

    override fun onDestroy() {
        if (isFinishing && !authenticated) {
            VaultSession.onAuthenticationDismissed()
        }
        super.onDestroy()
    }
}
