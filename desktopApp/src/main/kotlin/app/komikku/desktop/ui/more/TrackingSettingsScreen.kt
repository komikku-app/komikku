package app.komikku.desktop.ui.more

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.komikku.desktop.platform.DesktopBrowser
import app.komikku.desktop.track.DesktopTrackerLogin
import app.komikku.desktop.track.TrackerLoginMethod
import app.komikku.desktop.ui.browse.BrowseScaffold
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import exh.pref.DelegateSourcePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextString

/**
 * Desktop tracking settings (Android `SettingsTrackingScreen`): log in / out of each tracker with the
 * desktop login method ([DesktopTrackerLogin]), and the tracking behaviour preferences.
 */
class TrackingSettingsScreenModel(
    val trackerManager: TrackerManager = Injekt.get(),
    val trackPreferences: TrackPreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val delegateSourcePreferences: DelegateSourcePreferences = Injekt.get(),
    private val app: Application = Injekt.get(),
) : StateScreenModel<TrackingSettingsScreenModel.State>(State()) {

    @Immutable
    data class State(
        val loggedIn: Set<Long> = emptySet(),
        val dialog: Dialog? = null,
        val busy: Boolean = false,
        val error: String? = null,
        /** Trackers whose source is missing ([DesktopTrackerLogin.sourceAvailable]); kept current with the sources. */
        val needsSource: Set<Long> = emptySet(),
        val availabilityKnown: Boolean = false,
    )

    sealed interface Dialog {
        val tracker: Tracker

        data class Credentials(override val tracker: Tracker) : Dialog
        data class OAuthPaste(override val tracker: Tracker, val method: TrackerLoginMethod.OAuthPaste) : Dialog
        data class Logout(override val tracker: Tracker) : Dialog
    }

    init {
        screenModelScope.launch {
            trackerManager.loggedInTrackersFlow().collectLatest { trackers ->
                mutableState.update { it.copy(loggedIn = trackers.mapTo(mutableSetOf()) { tracker -> tracker.id }) }
            }
        }
        // Desktop sources load asynchronously, and MangaDex's availability also follows the language / source toggles
        screenModelScope.launch {
            combine(
                sourceManager.sources,
                sourcePreferences.enabledLanguages().changes(),
                sourcePreferences.disabledSources().changes(),
                delegateSourcePreferences.delegateSources().changes(),
            ) { _, _, _, _ -> }
                .collectLatest {
                    val missing = withIOContext {
                        trackerManager.trackers
                            .filterNot { DesktopTrackerLogin.sourceAvailable(it, sourceManager, sourcePreferences) }
                            .mapTo(mutableSetOf()) { it.id }
                    }
                    mutableState.update { it.copy(needsSource = missing, availabilityKnown = true) }
                }
        }
    }

    private fun needsSourceMessage(tracker: Tracker) =
        app.contextString(if (tracker is MdList) KMR.strings.desktop_mdlist_needs_source else KMR.strings.desktop_tracker_needs_source)

    fun startLogin(tracker: Tracker) {
        // Checked before the browser step: an authorization for MDList would be wasted without a MangaDex source
        if (tracker is MdList && !DesktopTrackerLogin.sourceAvailable(tracker, sourceManager, sourcePreferences)) {
            mutableState.update { it.copy(error = needsSourceMessage(tracker)) }
            return
        }
        when (val method = DesktopTrackerLogin.method(tracker)) {
            TrackerLoginMethod.Credentials -> mutableState.update { it.copy(dialog = Dialog.Credentials(tracker), error = null) }
            is TrackerLoginMethod.OAuthPaste -> {
                DesktopBrowser.open(method.authUrl())
                mutableState.update { it.copy(dialog = Dialog.OAuthPaste(tracker, method), error = null) }
            }
            TrackerLoginMethod.Enhanced -> (tracker as EnhancedTracker).loginNoop()
            TrackerLoginMethod.Unsupported -> {}
        }
    }

    fun loginWithCredentials(tracker: Tracker, username: String, password: String) = login {
        tracker.login(username, password)
        tracker.isLoggedIn
    }

    fun loginWithRedirect(tracker: Tracker, method: TrackerLoginMethod.OAuthPaste, pasted: String, invalidMessage: String) {
        if (DesktopTrackerLogin.credentialFromRedirect(method, pasted) == null) {
            mutableState.update { it.copy(error = invalidMessage) }
            return
        }
        if (tracker is MdList && !DesktopTrackerLogin.sourceAvailable(tracker, sourceManager, sourcePreferences)) {
            mutableState.update { it.copy(error = needsSourceMessage(tracker)) }
            return
        }
        // MDList: MangaDexLoginHelper.login (Android code) logs the cause and returns false; an authorization code works
        // once, so the message says to start again from the browser step
        val failed = if (tracker is MdList) KMR.strings.desktop_mdlist_login_failed else KMR.strings.login_failed
        login(failed) { DesktopTrackerLogin.loginWithRedirect(tracker, method, pasted) }
    }

    private fun login(failed: StringResource = KMR.strings.login_failed, block: suspend () -> Boolean) {
        mutableState.update { it.copy(busy = true, error = null) }
        screenModelScope.launch {
            val error = try {
                // A login that ends without an exception but still logged out
                if (withIOContext { block() }) null else app.contextString(failed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message?.takeIf { it.isNotBlank() } ?: app.contextString(MR.strings.unknown_error)
            }
            mutableState.update { it.copy(busy = false, error = error, dialog = if (error == null) null else it.dialog) }
        }
    }

    fun askLogout(tracker: Tracker) = mutableState.update { it.copy(dialog = Dialog.Logout(tracker)) }

    fun logout(tracker: Tracker) {
        mutableState.update { it.copy(busy = true) }
        screenModelScope.launch {
            try {
                withIOContext { DesktopTrackerLogin.logout(tracker, sourceManager, sourcePreferences) }
            } finally {
                dismiss()
            }
        }
    }

    fun dismiss() = mutableState.update { it.copy(dialog = null, error = null, busy = false) }
}

class TrackingSettingsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { TrackingSettingsScreenModel() }
        val state by model.state.collectAsState()
        val autoUpdate by model.trackPreferences.autoUpdateTrack().collectAsState()
        val onMarkRead by model.trackPreferences.autoUpdateTrackOnMarkRead().collectAsState()

        BrowseScaffold(title = stringResource(MR.strings.pref_category_tracking), onBack = navigator::pop) {
            LazyColumn(Modifier.fillMaxSize().testTag("tracking-settings"), contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    SwitchRow(
                        icon = null,
                        title = stringResource(MR.strings.pref_auto_update_manga_sync),
                        subtitle = null,
                        checked = autoUpdate,
                        tag = "tracking-auto-update",
                        onChange = { model.trackPreferences.autoUpdateTrack().set(it) },
                    )
                }
                item {
                    EnumRow(
                        title = stringResource(MR.strings.pref_auto_update_manga_on_mark_read),
                        value = onMarkRead,
                        values = AutoTrackState.entries,
                        label = { stringResource(it.titleRes) },
                        onChange = { model.trackPreferences.autoUpdateTrackOnMarkRead().set(it) },
                    )
                }
                items(model.trackerManager.trackers, key = { it.id }) { tracker ->
                    TrackerLoginRow(tracker, tracker.id in state.loggedIn, state, model)
                }
            }
        }

        when (val dialog = state.dialog) {
            is TrackingSettingsScreenModel.Dialog.Credentials -> CredentialsDialog(dialog.tracker, state, model)
            is TrackingSettingsScreenModel.Dialog.OAuthPaste -> OAuthPasteDialog(dialog, state, model)
            is TrackingSettingsScreenModel.Dialog.Logout -> AlertDialog(
                onDismissRequest = model::dismiss,
                title = { Text(stringResource(MR.strings.logout_title, dialog.tracker.name)) },
                confirmButton = {
                    TextButton(onClick = { model.logout(dialog.tracker) }, modifier = Modifier.testTag("tracker-logout-confirm")) {
                        Text(stringResource(MR.strings.logout))
                    }
                },
                dismissButton = { TextButton(onClick = model::dismiss) { Text(stringResource(MR.strings.action_cancel)) } },
            )
            null -> {}
        }
    }
}

@Composable
private fun TrackerLoginRow(
    tracker: Tracker,
    loggedIn: Boolean,
    state: TrackingSettingsScreenModel.State,
    model: TrackingSettingsScreenModel,
) {
    val method = DesktopTrackerLogin.method(tracker)
    // Enhanced trackers and MDList need a source; until the first check finished they are not offered
    val needsSourceCheck = method == TrackerLoginMethod.Enhanced || tracker is MdList
    val hasSource = !needsSourceCheck || (state.availabilityKnown && tracker.id !in state.needsSource)
    val available = when (method) {
        TrackerLoginMethod.Unsupported -> false
        else -> loggedIn || hasSource
    }
    val subtitle = when {
        loggedIn -> tracker.getUsername().takeIf { it.isNotBlank() && method != TrackerLoginMethod.Enhanced }
        method == TrackerLoginMethod.Unsupported -> stringResource(KMR.strings.desktop_tracker_not_available)
        !available && tracker is MdList -> stringResource(KMR.strings.desktop_mdlist_needs_source)
        !available -> stringResource(KMR.strings.desktop_tracker_needs_source)
        method == TrackerLoginMethod.Enhanced -> stringResource(MR.strings.enhanced_services)
        else -> null
    }
    ListItem(
        modifier = Modifier.testTag("tracker-login-row-${tracker.id}"),
        headlineContent = { Text(tracker.name) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            if (loggedIn) {
                OutlinedButton(onClick = { model.askLogout(tracker) }, modifier = Modifier.testTag("tracker-logout-${tracker.id}")) {
                    Text(stringResource(MR.strings.logout))
                }
            } else {
                OutlinedButton(onClick = { model.startLogin(tracker) }, enabled = available, modifier = Modifier.testTag("tracker-login-${tracker.id}")) {
                    Text(stringResource(MR.strings.login))
                }
            }
        },
    )
}

@Composable
private fun CredentialsDialog(tracker: Tracker, state: TrackingSettingsScreenModel.State, model: TrackingSettingsScreenModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = model::dismiss,
        modifier = Modifier.testTag("tracker-credentials"),
        title = { Text(tracker.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(MR.strings.username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tracker-username"),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(MR.strings.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("tracker-password"),
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { model.loginWithCredentials(tracker, username, password) },
                enabled = !state.busy && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.testTag("tracker-login-confirm"),
            ) { Text(stringResource(MR.strings.login)) }
        },
        dismissButton = { TextButton(onClick = model::dismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
private fun OAuthPasteDialog(
    dialog: TrackingSettingsScreenModel.Dialog.OAuthPaste,
    state: TrackingSettingsScreenModel.State,
    model: TrackingSettingsScreenModel,
) {
    var pasted by remember { mutableStateOf("") }
    val invalid = stringResource(KMR.strings.desktop_tracker_redirect_invalid, dialog.tracker.name)
    AlertDialog(
        onDismissRequest = model::dismiss,
        modifier = Modifier.testTag("tracker-oauth"),
        title = { Text(dialog.tracker.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(KMR.strings.desktop_tracker_paste_redirect))
                TextButton(onClick = { DesktopBrowser.open(dialog.method.authUrl()) }) {
                    Text(stringResource(KMR.strings.desktop_tracker_open_login))
                }
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = { Text(stringResource(KMR.strings.desktop_tracker_redirect_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tracker-redirect"),
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { model.loginWithRedirect(dialog.tracker, dialog.method, pasted, invalid) },
                enabled = !state.busy && pasted.isNotBlank(),
                modifier = Modifier.testTag("tracker-login-confirm"),
            ) { Text(stringResource(MR.strings.login)) }
        },
        dismissButton = { TextButton(onClick = model::dismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
fun <T> EnumRow(title: String, value: T, values: List<T>, label: @Composable (T) -> String, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.testTag("enum-$title"),
        headlineContent = { Text(title) },
        trailingContent = {
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(label(value)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    values.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(label(option)) },
                            onClick = {
                                expanded = false
                                onChange(option)
                            },
                        )
                    }
                }
            }
        },
    )
}
