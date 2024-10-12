package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesScreen
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSecurityScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        val privacyPreferences = remember { Injekt.get<PrivacyPreferences>() }
        return listOf(
            getSecurityGroup(securityPreferences),
            getFirebaseGroup(privacyPreferences),
        )
    }

    @Composable
    private fun getSecurityGroup(
        securityPreferences: SecurityPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val authSupported = remember { context.isAuthenticationSupported() }
        val useAuthPref = securityPreferences.useAuthenticator()
        val useAuth by useAuthPref.collectAsState()

        val scope = rememberCoroutineScope()
        val isCbzPasswordSet by remember { CbzCrypto.isPasswordSetState(scope) }.collectAsState()
        val passwordProtectDownloads by securityPreferences.passwordProtectDownloads().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_security),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = useAuthPref,
                    title = stringResource(MR.strings.lock_with_biometrics),
                    enabled = authSupported,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_with_biometrics),
                        )
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = securityPreferences.lockAppAfter(),
                    title = stringResource(MR.strings.lock_when_idle),
                    enabled = authSupported && useAuth,
                    entries = LockAfterValues
                        .associateWith {
                            when (it) {
                                -1 -> stringResource(MR.strings.lock_never)
                                0 -> stringResource(MR.strings.lock_always)
                                else -> pluralStringResource(MR.plurals.lock_after_mins, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_when_idle),
                        )
                    },
                ),

                Preference.PreferenceItem.SwitchPreference(
                    pref = securityPreferences.hideNotificationContent(),
                    title = stringResource(MR.strings.hide_notification_content),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = securityPreferences.secureScreen(),
                    title = stringResource(MR.strings.secure_screen),
                    entries = SecurityPreferences.SecureScreenMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                ),
                // SY -->
                Preference.PreferenceItem.SwitchPreference(
                    pref = securityPreferences.passwordProtectDownloads(),
                    title = stringResource(SYMR.strings.password_protect_downloads),
                    subtitle = stringResource(SYMR.strings.password_protect_downloads_summary),
                    enabled = isCbzPasswordSet,
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = securityPreferences.encryptionType(),
                    title = stringResource(SYMR.strings.encryption_type),
                    entries = SecurityPreferences.EncryptionType.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    enabled = passwordProtectDownloads,

                ),
                kotlin.run {
                    var dialogOpen by remember { mutableStateOf(false) }
                    if (dialogOpen) {
                        PasswordDialog(
                            onDismissRequest = { dialogOpen = false },
                            onReturnPassword = { password ->
                                dialogOpen = false

                                CbzCrypto.deleteKeyCbz()
                                securityPreferences.cbzPassword().set(CbzCrypto.encryptCbz(password.replace("\n", "")))
                            },
                        )
                    }
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(SYMR.strings.set_cbz_zip_password),
                        onClick = {
                            dialogOpen = true
                        },
                    )
                },
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(SYMR.strings.delete_cbz_archive_password),
                    onClick = {
                        CbzCrypto.deleteKeyCbz()
                        securityPreferences.cbzPassword().set("")
                    },
                    enabled = isCbzPasswordSet,
                ),
                kotlin.run {
                    val navigator = LocalNavigator.currentOrThrow
                    val count by securityPreferences.authenticatorTimeRanges().collectAsState()
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(SYMR.strings.action_edit_biometric_lock_times),
                        subtitle = pluralStringResource(
                            SYMR.plurals.num_lock_times,
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
                        title = stringResource(SYMR.strings.biometric_lock_days),
                        subtitle = stringResource(SYMR.strings.biometric_lock_days_summary),
                        onClick = { dialogOpen = true },
                        enabled = useAuth,
                    )
                },
                // SY <--
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.secure_screen_summary)),
            ),
        )
    }

    @Composable
    private fun getFirebaseGroup(
        privacyPreferences: PrivacyPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_firebase),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = privacyPreferences.crashlytics(),
                    title = stringResource(MR.strings.onboarding_permission_crashlytics),
                    subtitle = stringResource(MR.strings.onboarding_permission_crashlytics_description),
                ),
                /*
                Preference.PreferenceItem.SwitchPreference(
                    pref = privacyPreferences.analytics(),
                    title = stringResource(MR.strings.onboarding_permission_analytics),
                    subtitle = stringResource(MR.strings.onboarding_permission_analytics_description),
                ),
                 */
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.firebase_summary)),
            ),
        )
    }

    // SY -->
    enum class DayOption(val day: Int, val stringRes: StringResource) {
        Sunday(SecureActivityDelegate.LOCK_SUNDAY, SYMR.strings.sunday),
        Monday(SecureActivityDelegate.LOCK_MONDAY, SYMR.strings.monday),
        Tuesday(SecureActivityDelegate.LOCK_TUESDAY, SYMR.strings.tuesday),
        Wednesday(SecureActivityDelegate.LOCK_WEDNESDAY, SYMR.strings.wednesday),
        Thursday(SecureActivityDelegate.LOCK_THURSDAY, SYMR.strings.thursday),
        Friday(SecureActivityDelegate.LOCK_FRIDAY, SYMR.strings.friday),
        Saturday(SecureActivityDelegate.LOCK_SATURDAY, SYMR.strings.saturday),
    }

    @Composable
    fun SetLockedDaysDialog(
        onDismissRequest: () -> Unit,
        initialSelection: Int,
        onDaysSelected: (Int) -> Unit,
    ) {
        val selected = remember(initialSelection) {
            DayOption.entries.filter { it.day and initialSelection == it.day }
                .toMutableStateList()
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(SYMR.strings.biometric_lock_days)) },
            text = {
                LazyColumn {
                    DayOption.entries.forEach { day ->
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
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    @Composable
    fun PasswordDialog(
        onDismissRequest: () -> Unit,
        onReturnPassword: (String) -> Unit,
    ) {
        var password by rememberSaveable { mutableStateOf("") }
        var passwordVisibility by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onDismissRequest,

            title = { Text(text = stringResource(SYMR.strings.cbz_archive_password)) },
            text = {
                TextField(
                    value = password,
                    onValueChange = { password = it },

                    maxLines = 1,
                    placeholder = { Text(text = stringResource(MR.strings.password)) },
                    label = { Text(text = stringResource(MR.strings.password)) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                passwordVisibility = !passwordVisibility
                            },
                        ) {
                            Icon(
                                imageVector = if (passwordVisibility) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                },
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onReturnPassword(password) },
                    ),
                    modifier = Modifier.onKeyEvent {
                        if (it.key == Key.Enter) {
                            return@onKeyEvent true
                        }
                        false
                    },
                    visualTransformation = if (passwordVisibility) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                )
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        onReturnPassword(password)
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
    // SY <--
}

private val LockAfterValues = persistentListOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)
