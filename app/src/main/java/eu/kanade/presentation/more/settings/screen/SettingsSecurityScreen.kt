package eu.kanade.presentation.more.settings.screen

import android.widget.Toast
import androidx.annotation.StringRes
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import eu.kanade.tachiyomi.util.storage.CbzCrypto
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

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

        val scope = rememberCoroutineScope()
        val isCbzPasswordSet by remember { CbzCrypto.isPasswordSetState(scope) }.collectAsState()
        val passwordProtectDownloads by securityPreferences.passwordProtectDownloads().collectAsState()

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
            Preference.PreferenceItem.SwitchPreference(
                title = stringResource(R.string.encrypt_database),
                pref = securityPreferences.encryptDatabase(),
                subtitle = stringResource(R.string.encrypt_database_subtitle),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = securityPreferences.passwordProtectDownloads(),
                title = stringResource(R.string.password_protect_downloads),
                subtitle = stringResource(R.string.password_protect_downloads_summary),
                enabled = isCbzPasswordSet,
            ),
            Preference.PreferenceItem.ListPreference(
                pref = securityPreferences.encryptionType(),
                title = stringResource(R.string.encryption_type),
                entries = SecurityPreferences.EncryptionType.values()
                    .associateWith { stringResource(it.titleResId) },
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
                    title = stringResource(R.string.set_cbz_zip_password),
                    onClick = {
                        dialogOpen = true
                    },
                )
            },
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.delete_cbz_archive_password),
                onClick = {
                    CbzCrypto.deleteKeyCbz()
                    securityPreferences.cbzPassword().set("")
                },
                enabled = isCbzPasswordSet,
            ),
            Preference.PreferenceItem.ListPreference(
                pref = securityPreferences.localCoverLocation(),
                title = stringResource(R.string.save_local_manga_covers),
                entries = SecurityPreferences.CoverCacheLocation.values()
                    .associateWith { stringResource(it.titleResId) },
                enabled = passwordProtectDownloads,
                onValueChanged = {
                    try {
                        withIOContext {
                            CbzCrypto.deleteLocalCoverCache(context)
                            CbzCrypto.deleteLocalCoverSystemFiles(context)
                        }
                        true
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e)
                        context.toast(e.toString(), Toast.LENGTH_SHORT).show()
                        false
                    }
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.delete_cached_local_source_covers),
                subtitle = stringResource(R.string.delete_cached_local_source_covers_subtitle),
                onClick = {
                    try {
                        CbzCrypto.deleteLocalCoverCache(context)
                        CbzCrypto.deleteLocalCoverSystemFiles(context)
                        context.toast(R.string.successfully_deleted_all_locally_cached_covers, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e)
                        context.toast(R.string.something_went_wrong_deleting_your_cover_images, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = produceState(false) {
                    withIOContext {
                        value = context.getExternalFilesDir("covers/local")?.absolutePath?.let { File(it).listFiles()?.isNotEmpty() } == true
                    }
                }.value,
            ),
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

    @Composable
    fun PasswordDialog(
        onDismissRequest: () -> Unit,
        onReturnPassword: (String) -> Unit,
    ) {
        var password by rememberSaveable { mutableStateOf("") }
        var passwordVisibility by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onDismissRequest,

            title = { Text(text = stringResource(R.string.cbz_archive_password)) },
            text = {
                TextField(
                    value = password,
                    onValueChange = { password = it },

                    maxLines = 1,
                    placeholder = { Text(text = stringResource(R.string.password)) },
                    label = { Text(text = stringResource(R.string.password)) },
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
