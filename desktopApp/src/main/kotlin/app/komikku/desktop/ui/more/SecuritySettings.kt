package app.komikku.desktop.ui.more

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.komikku.desktop.security.DesktopAppLock
import app.komikku.desktop.ui.security.AppLockDialogMode
import app.komikku.desktop.ui.security.AppLockSecretDialog
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * P16: Android's `SettingsSecurityScreen` security group with what the desktop has: the lock (a PIN / password of the
 * app's own instead of the device's authenticator, see [DesktopAppLock]), changing it, "Lock now" and Android's "Lock
 * when idle". Not on desktop: secure screen and notification content (no such window flag / notifications yet), SY's
 * CBZ password and lock times / days editors, Firebase.
 */
@Composable
internal fun securityPreferences(): List<Preference> {
    val lock = remember { Injekt.get<DesktopAppLock>() }
    val security = remember { Injekt.get<SecurityPreferences>() }
    val enabled by lock.enabled.collectAsState()
    var dialog by remember { mutableStateOf<AppLockDialogMode?>(null) }
    dialog?.let { mode -> AppLockSecretDialog(mode, lock, onDismiss = { dialog = null }) }
    return listOf(
        Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_security),
            preferenceItems = persistentListOf(
                // Not a SwitchPreference: on means "Require unlock" *and* a PIN / password, and either way a dialog decides
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(KMR.strings.desktop_app_lock),
                    subtitle = stringResource(KMR.strings.desktop_app_lock_summary),
                    widget = { Switch(checked = enabled, onCheckedChange = null, modifier = Modifier.testTag("app-lock-switch")) },
                    onClick = { dialog = if (enabled) AppLockDialogMode.TurnOff else AppLockDialogMode.TurnOn },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(KMR.strings.desktop_app_lock_change),
                    enabled = enabled,
                    onClick = { dialog = AppLockDialogMode.Change },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(KMR.strings.desktop_app_lock_now),
                    enabled = enabled,
                    onClick = lock::lockNow,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = security.lockAppAfter(),
                    entries = LockAfterValues
                        .associateWith {
                            when (it) {
                                -1 -> stringResource(MR.strings.lock_never)
                                0 -> stringResource(MR.strings.lock_always)
                                else -> pluralStringResource(MR.plurals.lock_after_mins, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.lock_when_idle),
                    enabled = enabled,
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.desktop_app_lock_info)),
            ),
        ),
    )
}

/** Android's `LockAfterValues` (minutes; 0 = always, -1 = never). */
private val LockAfterValues = listOf(0, 1, 2, 5, 10, -1)
