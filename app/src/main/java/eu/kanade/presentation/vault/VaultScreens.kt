package eu.kanade.presentation.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Neutral screen shown behind the device credential prompt. It must never render vault content.
 */
@Composable
fun VaultLockedScreen(
    onUnlock: () -> Unit,
    onUseStandardLibrary: () -> Unit,
) {
    VaultGateContent(
        message = stringResource(KMR.strings.private_vault_locked),
        showUnlock = true,
        onUnlock = onUnlock,
        onUseStandardLibrary = onUseStandardLibrary,
    )
}

/**
 * Screen of the activity that hosts the device credential prompt.
 */
@Composable
fun VaultUnlockScreen(
    hasDeviceLock: Boolean,
    onUnlock: () -> Unit,
    onUseStandardLibrary: () -> Unit,
) {
    VaultGateContent(
        message = if (hasDeviceLock) {
            stringResource(KMR.strings.private_vault_unlock_hint)
        } else {
            stringResource(KMR.strings.private_vault_no_device_lock)
        },
        showUnlock = hasDeviceLock,
        onUnlock = onUnlock,
        onUseStandardLibrary = onUseStandardLibrary,
    )
}

@Composable
private fun VaultGateContent(
    message: String,
    showUnlock: Boolean,
    onUnlock: () -> Unit,
    onUseStandardLibrary: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(KMR.strings.private_vault),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (showUnlock) {
                Button(onClick = onUnlock) {
                    Text(text = stringResource(KMR.strings.private_vault_unlock))
                }
            }
            TextButton(onClick = onUseStandardLibrary) {
                Text(text = stringResource(KMR.strings.private_vault_use_standard_library))
            }
        }
    }
}
