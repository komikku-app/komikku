package eu.kanade.presentation.more.settings.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesScreen
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSecurityScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    @StringRes
    override fun getTitleRes() = R.string.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        val authSupported = remember { context.isAuthenticationSupported() }

        val useAuthPref = securityPreferences.useAuthenticator()

        val useAuth by useAuthPref.collectAsState()

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = useAuthPref,
                title = stringResource(R.string.lock_with_biometrics),
                enabled = authSupported,
                onValueChanged = {
                    (context as FragmentActivity).authenticate(
                        title = context.getString(R.string.lock_with_biometrics),
                    )
                },
            ),
            Preference.PreferenceItem.ListPreference(
                pref = securityPreferences.lockAppAfter(),
                title = stringResource(R.string.lock_when_idle),
                enabled = authSupported && useAuth,
                entries = LockAfterValues
                    .associateWith {
                        when (it) {
                            -1 -> stringResource(R.string.lock_never)
                            0 -> stringResource(R.string.lock_always)
                            else -> pluralStringResource(id = R.plurals.lock_after_mins, count = it, it)
                        }
                    },
                onValueChanged = {
                    (context as FragmentActivity).authenticate(
                        title = context.getString(R.string.lock_when_idle),
                    )
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = securityPreferences.hideNotificationContent(),
                title = stringResource(R.string.hide_notification_content),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = securityPreferences.secureScreen(),
                title = stringResource(R.string.secure_screen),
                entries = SecurityPreferences.SecureScreenMode.values()
                    .associateWith { stringResource(it.titleResId) },
            ),
            // SY -->
            kotlin.run {
                val navigator = LocalNavigator.currentOrThrow
                val count by securityPreferences.authenticatorTimeRanges().collectAsState()
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.action_edit_biometric_lock_times),
                    subtitle = pluralStringResource(
                        R.plurals.num_lock_times,
                        count.size,
                        count.size,
                    ),
                    onClick = {
                        navigator.push(BiometricTimesScreen())
                    },
                    enabled = useAuth,
                )
            },
            kotlin.run {
                val selection by securityPreferences.authenticatorDays().collectAsState()
                var dialogOpen by remember { mutableStateOf(false) }
                if (dialogOpen) {
                    SetLockedDaysDialog(
                        onDismissRequest = { dialogOpen = false },
                        initialSelection = selection,
                        onDaysSelected = {
                            dialogOpen = false
                            securityPreferences.authenticatorDays().set(it)
                        },
                    )
                }
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.biometric_lock_days),
                    subtitle = stringResource(R.string.biometric_lock_days_summary),
                    onClick = { dialogOpen = true },
                    enabled = useAuth,
                )
            },
            // SY <--
            Preference.PreferenceItem.InfoPreference(stringResource(R.string.secure_screen_summary)),
        )
    }

    // SY -->
    enum class DayOption(val day: Int, val stringRes: Int) {
        Sunday(SecureActivityDelegate.LOCK_SUNDAY, R.string.sunday),
        Monday(SecureActivityDelegate.LOCK_MONDAY, R.string.monday),
        Tuesday(SecureActivityDelegate.LOCK_TUESDAY, R.string.tuesday),
        Wednesday(SecureActivityDelegate.LOCK_WEDNESDAY, R.string.wednesday),
        Thursday(SecureActivityDelegate.LOCK_THURSDAY, R.string.thursday),
        Friday(SecureActivityDelegate.LOCK_FRIDAY, R.string.friday),
        Saturday(SecureActivityDelegate.LOCK_SATURDAY, R.string.saturday),
    }

    @Composable
    fun SetLockedDaysDialog(
        onDismissRequest: () -> Unit,
        initialSelection: Int,
        onDaysSelected: (Int) -> Unit,
    ) {
        val selected = remember(initialSelection) {
            DayOption.values().filter { it.day and initialSelection == it.day }
                .toMutableStateList()
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(R.string.biometric_lock_days)) },
            text = {
                LazyColumn {
                    DayOption.values().forEach { day ->
                        item {
                            val isSelected = selected.contains(day)
                            val onSelectionChanged = {
                                when (!isSelected) {
                                    true -> selected.add(day)
                                    false -> selected.remove(day)
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectionChanged() },
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onSelectionChanged() },
                                )
                                Text(
                                    text = stringResource(day.stringRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        onDaysSelected(
                            selected.fold(0) { i, day ->
                                i or day.day
                            },
                        )
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
    // SY <--
}

private val LockAfterValues = listOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)
