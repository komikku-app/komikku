package app.komikku.desktop.ui.security

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.komikku.desktop.security.DesktopAppLock
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * P16: the app behind Android's `UnlockActivity`. While [lock] is locked, the lock screen covers [content], which stays
 * composed (its screens and the reader keep their state, like Android's activities under the unlock screen) but cannot
 * be focused, clicked or read by accessibility services.
 */
@Composable
fun AppLockGate(lock: DesktopAppLock, content: @Composable () -> Unit) {
    val locked by lock.locked.collectAsState()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(locked) { if (locked) focusManager.clearFocus(force = true) }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (locked) {
                        Modifier
                            .clearAndSetSemantics {}
                            .focusProperties { onEnter = { cancelFocusChange() } }
                            .focusGroup()
                    } else {
                        Modifier
                    },
                ),
        ) { content() }
        if (locked) AppLockScreen(lock)
    }
}

/** Android's unlock prompt ("Unlock Komikku") with a PIN / password field. */
@Composable
private fun AppLockScreen(lock: DesktopAppLock) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val wrong = stringResource(KMR.strings.desktop_app_lock_wrong)
    val waitText = stringResource(KMR.strings.desktop_app_lock_wait)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val submit = submit@{
        if (checking || input.isEmpty()) return@submit
        checking = true
        scope.launch {
            try {
                when (val attempt = lock.unlock(input)) {
                    DesktopAppLock.Attempt.Accepted -> error = null
                    DesktopAppLock.Attempt.Wrong -> error = wrong
                    is DesktopAppLock.Attempt.Blocked -> error = waitText.replace("%d", attempt.seconds.toString())
                }
                input = ""
            } finally {
                checking = false
            }
        }
        Unit
    }
    Surface(modifier = Modifier.fillMaxSize().testTag("app-lock")) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 360.dp).padding(24.dp),
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(MR.strings.unlock_app_title, stringResource(MR.strings.app_name)),
                    style = MaterialTheme.typography.headlineSmall,
                )
                SecretField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    label = stringResource(KMR.strings.desktop_app_lock_input),
                    error = error,
                    onDone = submit,
                    modifier = Modifier.focusRequester(focusRequester).testTag("app-lock-input"),
                )
                Button(
                    onClick = submit,
                    enabled = !checking && input.isNotEmpty(),
                    modifier = Modifier.testTag("app-lock-unlock"),
                ) { Text(stringResource(KMR.strings.desktop_app_lock_unlock)) }
            }
        }
    }
}

/** A masked PIN / password field. */
@Composable
internal fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it, modifier = Modifier.testTag("app-lock-error")) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
    )
}

/** What a PIN / password dialog of the security settings does. */
enum class AppLockDialogMode { TurnOn, TurnOff, Change }

/**
 * The security settings' PIN / password dialogs: set one (twice) to turn the lock on, enter it to turn the lock off,
 * enter the current one and a new one (twice) to change it. Closes itself once [lock] accepted the change.
 */
@Composable
fun AppLockSecretDialog(mode: AppLockDialogMode, lock: DesktopAppLock, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var currentError by remember { mutableStateOf<String?>(null) }
    var newError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val wrong = stringResource(KMR.strings.desktop_app_lock_wrong)
    val waitText = stringResource(KMR.strings.desktop_app_lock_wait)
    val tooShort = stringResource(KMR.strings.desktop_app_lock_too_short, DesktopAppLock.MIN_LENGTH)
    val mismatch = stringResource(KMR.strings.desktop_app_lock_mismatch)
    val asksCurrent = mode != AppLockDialogMode.TurnOn
    val asksNew = mode != AppLockDialogMode.TurnOff

    fun attemptError(attempt: DesktopAppLock.Attempt): String? = when (attempt) {
        DesktopAppLock.Attempt.Accepted -> null
        DesktopAppLock.Attempt.Wrong -> wrong
        is DesktopAppLock.Attempt.Blocked -> waitText.replace("%d", attempt.seconds.toString())
    }

    val confirm = confirm@{
        if (busy) return@confirm
        if (asksNew) {
            newError = when {
                new.length < DesktopAppLock.MIN_LENGTH -> tooShort
                new != repeat -> mismatch
                else -> null
            }
            if (newError != null) return@confirm
        }
        busy = true
        scope.launch {
            try {
                when (mode) {
                    AppLockDialogMode.TurnOn -> {
                        lock.enable(new)
                        onDismiss()
                    }
                    AppLockDialogMode.TurnOff -> {
                        currentError = attemptError(lock.disable(current))
                        if (currentError == null) onDismiss()
                    }
                    AppLockDialogMode.Change -> {
                        currentError = attemptError(lock.change(current, new))
                        if (currentError == null) onDismiss()
                    }
                }
            } finally {
                busy = false
            }
        }
        Unit
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("app-lock-dialog"),
        title = {
            Text(
                stringResource(
                    when (mode) {
                        AppLockDialogMode.TurnOn -> KMR.strings.desktop_app_lock_set
                        AppLockDialogMode.TurnOff -> KMR.strings.desktop_app_lock_turn_off
                        AppLockDialogMode.Change -> KMR.strings.desktop_app_lock_change
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (asksCurrent) {
                    SecretField(
                        value = current,
                        onValueChange = {
                            current = it
                            currentError = null
                        },
                        label = stringResource(KMR.strings.desktop_app_lock_current),
                        error = currentError,
                        onDone = confirm,
                        modifier = Modifier.testTag("app-lock-current"),
                    )
                }
                if (asksNew) {
                    SecretField(
                        value = new,
                        onValueChange = {
                            new = it
                            newError = null
                        },
                        label = stringResource(KMR.strings.desktop_app_lock_new),
                        error = null,
                        onDone = confirm,
                        modifier = Modifier.testTag("app-lock-new"),
                    )
                    SecretField(
                        value = repeat,
                        onValueChange = {
                            repeat = it
                            newError = null
                        },
                        label = stringResource(KMR.strings.desktop_app_lock_repeat),
                        error = newError,
                        onDone = confirm,
                        modifier = Modifier.testTag("app-lock-repeat"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = !busy, modifier = Modifier.testTag("app-lock-confirm")) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}
