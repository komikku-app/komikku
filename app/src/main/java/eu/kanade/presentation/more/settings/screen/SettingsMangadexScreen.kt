package eu.kanade.presentation.more.settings.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogW
import exh.md.utils.MdUtil
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsMangadexScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    @StringRes
    override fun getTitleRes() = R.string.pref_category_mangadex

    override fun isEnabled(): Boolean = MdUtil.getEnabledMangaDexs(Injekt.get()).isNotEmpty()

    @Composable
    override fun getPreferences(): List<Preference> {
        val sourcePreferences: SourcePreferences = remember { Injekt.get() }
        val unsortedPreferences: UnsortedPreferences = remember { Injekt.get() }
        val mdex = remember { MdUtil.getEnabledMangaDex(unsortedPreferences, sourcePreferences) } ?: return emptyList()

        return listOf(
            loginPreference(mdex),
            preferredMangaDexId(unsortedPreferences, sourcePreferences),
            syncMangaDexIntoThis(unsortedPreferences),
            syncLibraryToMangaDex(),
        )
    }

    @Composable
    fun LoginDialog(
        mdex: MangaDex,
        onDismissRequest: () -> Unit,
        onLoginSuccess: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var username by remember { mutableStateOf(TextFieldValue(mdex.getUsername())) }
        var password by remember { mutableStateOf(TextFieldValue(mdex.getPassword())) }
        var processing by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(R.string.login_title, mdex.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(text = stringResource(R.string.username)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        isError = inputError && username.text.isEmpty(),
                    )

                    var hidePassword by remember { mutableStateOf(true) }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(text = stringResource(R.string.password)) },
                        trailingIcon = {
                            IconButton(onClick = { hidePassword = !hidePassword }) {
                                Icon(
                                    imageVector = if (hidePassword) {
                                        Icons.Default.Visibility
                                    } else {
                                        Icons.Default.VisibilityOff
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        visualTransformation = if (hidePassword) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        singleLine = true,
                        isError = inputError && password.text.isEmpty(),
                    )
                }
            },
            confirmButton = {
                Column {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !processing,
                        onClick = {
                            if (username.text.isEmpty() || password.text.isEmpty()) {
                                inputError = true
                                return@Button
                            }
                            scope.launchIO {
                                try {
                                    inputError = false
                                    processing = true
                                    val result = mdex.login(
                                        username = username.text,
                                        password = password.text,
                                        twoFactorCode = null,
                                    )
                                    if (result) {
                                        onDismissRequest()
                                        onLoginSuccess()
                                        withUIContext {
                                            context.toast(R.string.login_success)
                                        }
                                    }
                                } catch (e: Exception) {
                                    xLogW("Login to Mangadex error", e)
                                    withUIContext {
                                        e.message?.let { context.toast(it) }
                                    }
                                } finally {
                                    processing = false
                                }
                            }
                        },
                    ) {
                        val id = if (processing) R.string.loading else R.string.login
                        Text(text = stringResource(id))
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onDismissRequest,
                    ) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = !processing,
                dismissOnClickOutside = !processing,
            ),
        )
    }

    @Composable
    fun LogoutDialog(
        onDismissRequest: () -> Unit,
        onLogoutRequest: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(R.string.logout))
            },
            confirmButton = {
                TextButton(onClick = onLogoutRequest) {
                    Text(text = stringResource(R.string.logout))
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
    fun loginPreference(mdex: MangaDex): Preference.PreferenceItem.MangaDexPreference {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var loggedIn by remember { mutableStateOf(mdex.isLogged()) }
        var loginDialogOpen by remember { mutableStateOf(false) }
        if (loginDialogOpen) {
            LoginDialog(
                mdex = mdex,
                onDismissRequest = { loginDialogOpen = false },
                onLoginSuccess = { loggedIn = true },
            )
        }
        var logoutDialogOpen by remember { mutableStateOf(false) }
        if (logoutDialogOpen) {
            LogoutDialog(
                onDismissRequest = { logoutDialogOpen = false },
                onLogoutRequest = {
                    logoutDialogOpen = false
                    scope.launchIO {
                        try {
                            if (mdex.logout()) {
                                loggedIn = false
                                withUIContext {
                                    context.toast(R.string.logout_success)
                                }
                            } else {
                                withUIContext {
                                    context.toast(R.string.unknown_error)
                                }
                            }
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "Logout error" }
                            withUIContext {
                                context.toast(R.string.unknown_error)
                            }
                        }
                    }
                },
            )
        }
        return Preference.PreferenceItem.MangaDexPreference(
            title = mdex.name + " Login",
            loggedIn = loggedIn,
            login = {
                loginDialogOpen = true
            },
            logout = {
                logoutDialogOpen = true
            },
        )
    }

    @Composable
    fun preferredMangaDexId(
        unsortedPreferences: UnsortedPreferences,
        sourcePreferences: SourcePreferences,
    ): Preference.PreferenceItem.ListPreference<String> {
        return Preference.PreferenceItem.ListPreference(
            pref = unsortedPreferences.preferredMangaDexId(),
            title = stringResource(R.string.mangadex_preffered_source),
            subtitle = stringResource(R.string.mangadex_preffered_source_summary),
            entries = MdUtil.getEnabledMangaDexs(sourcePreferences)
                .associate { it.id.toString() to it.toString() },
        )
    }

    @Composable
    fun SyncMangaDexDialog(
        onDismissRequest: () -> Unit,
        onSelectionConfirmed: (List<String>) -> Unit,
    ) {
        val context = LocalContext.current
        val items = remember {
            context.resources.getStringArray(R.array.md_follows_options)
                .drop(1)
        }
        val selection = remember {
            List(items.size) { index ->
                index == 0 || index == 5
            }.toMutableStateList()
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(R.string.mangadex_sync_follows_to_library))
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    items.forEachIndexed { index, followOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val checked = selection.getOrNull(index) ?: false
                                    selection[index] = !checked
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selection.getOrNull(index) ?: false,
                                onCheckedChange = null,
                            )

                            Text(
                                text = followOption,
                                modifier = Modifier.padding(horizontal = horizontalPadding),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSelectionConfirmed(items.filterIndexed { index, _ -> selection[index] }) }) {
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
    fun syncMangaDexIntoThis(unsortedPreferences: UnsortedPreferences): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            SyncMangaDexDialog(
                onDismissRequest = { dialogOpen = false },
                onSelectionConfirmed = { items ->
                    dialogOpen = false
                    unsortedPreferences.mangadexSyncToLibraryIndexes().set(
                        List(items.size) { index -> (index + 1).toString() }.toSet(),
                    )
                    LibraryUpdateService.start(
                        context,
                        target = LibraryUpdateService.Target.SYNC_FOLLOWS,
                    )
                },
            )
        }
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(R.string.mangadex_sync_follows_to_library),
            subtitle = stringResource(R.string.mangadex_sync_follows_to_library_summary),
            onClick = { dialogOpen = true },
        )
    }

    @Composable
    fun syncLibraryToMangaDex(): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(R.string.mangadex_push_favorites_to_mangadex),
            subtitle = stringResource(R.string.mangadex_push_favorites_to_mangadex_summary),
            onClick = {
                LibraryUpdateService.start(
                    context,
                    target = LibraryUpdateService.Target.PUSH_FAVORITES,
                )
            },
        )
    }
}
