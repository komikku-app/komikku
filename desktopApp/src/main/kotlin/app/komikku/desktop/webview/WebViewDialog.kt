package app.komikku.desktop.webview

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.komikku.network.browser.BrowserSessionResult
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as localized

/**
 * KMK: UI state of the desktop "Open in WebView" ([DesktopWebView]) for one screen: [open] runs the embedded browser
 * or opens the system browser and shows the cookie dialog ([WebViewDialogs]).
 */
class WebViewLauncher(
    private val webView: DesktopWebView,
    private val scope: CoroutineScope,
    private val app: Application,
) {
    sealed interface State {
        data object Idle : State

        /** The address is resolved or the embedded browser is open; [cancel] ends it. */
        data object Running : State

        data class Manual(val entry: DesktopWebView.ManualEntry, val error: DesktopWebView.SaveResult? = null) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    private var job: Job? = null

    fun open(url: String, sourceId: Long?) {
        open(sourceId) { url }
    }

    /**
     * Resolves the address with [resolveUrl] off the UI thread (it may call the source, e.g. `getMangaUrl`; `null`: the
     * entry has no address) and opens it. Every failure ends in [State.Idle] with a message.
     */
    fun open(sourceId: Long?, resolveUrl: () -> String?): Job? {
        if (state != State.Idle) return null
        state = State.Running
        return scope.launch {
            var address: String? = null
            try {
                // Interruptible: cancelling the dialog interrupts the thread a blocking browser session runs on.
                val opened = runInterruptible(Dispatchers.IO) {
                    address = resolveUrl()
                    address?.let { webView.open(it, sourceId) }
                }
                state = when (opened) {
                    null -> {
                        app.toast(KMR.strings.desktop_webview_no_address)
                        State.Idle
                    }
                    is DesktopWebView.Opened.Embedded -> {
                        val host = opened.result.finalUrl?.host ?: address.orEmpty().hostOf()
                        app.toast(
                            when {
                                opened.result.cookies.isNotEmpty() ->
                                    app.localized(KMR.strings.desktop_webview_cookies_saved, host)
                                opened.result.outcome == BrowserSessionResult.Outcome.TIMED_OUT ->
                                    app.localized(KMR.strings.desktop_webview_timed_out, host)
                                else -> app.localized(KMR.strings.desktop_webview_no_cookies, host)
                            },
                        )
                        State.Idle
                    }
                    is DesktopWebView.Opened.Manual -> State.Manual(opened.entry)
                    is DesktopWebView.Opened.InvalidUrl -> {
                        app.toast(app.localized(KMR.strings.desktop_source_error, opened.url))
                        State.Idle
                    }
                }
            } catch (e: CancellationException) {
                // [cancel] already reset the state (or the screen is gone).
                throw e
            } catch (e: Throwable) {
                xLogE("Open in WebView failed", e)
                state = State.Idle
                app.toast(
                    app.localized(
                        KMR.strings.desktop_webview_failed,
                        address?.hostOf() ?: "",
                        e.message ?: e::class.simpleName.orEmpty(),
                    ),
                )
            }
        }.also { job = it }
    }

    /** Cancels a running [open] (and with it an embedded browser session). */
    fun cancel() {
        if (state != State.Running) return
        job?.cancel()
        state = State.Idle
    }

    fun save(cookies: String, userAgent: String, alsoDefault: Boolean = false) {
        val manual = state as? State.Manual ?: return
        when (val result = webView.saveManual(manual.entry, cookies, userAgent, alsoDefault)) {
            is DesktopWebView.SaveResult.Saved -> {
                state = State.Idle
                if (result.cookies > 0) app.toast(app.localized(KMR.strings.desktop_webview_cookies_saved, manual.entry.host))
                if (result.skipped.isNotEmpty()) {
                    app.toast(app.localized(KMR.strings.desktop_webview_cookies_skipped, result.skipped.joinToString()))
                }
                if (result.defaultUserAgentChanged) app.toast(MR.strings.requires_app_restart)
            }
            else -> state = manual.copy(error = result)
        }
    }

    fun reopenBrowser() {
        val manual = state as? State.Manual ?: return
        webView.openInBrowser(manual.entry.url)
    }

    fun clearCookies() {
        val manual = state as? State.Manual ?: return
        webView.clearCookies(manual.entry.url)
        state = manual.copy(
            entry = manual.entry.copy(storedCookies = emptyList(), userAgent = manual.entry.sourceUserAgent),
            error = null,
        )
        app.toast(MR.strings.cookies_cleared)
    }

    fun dismiss() {
        if (state is State.Manual) state = State.Idle
    }

    private fun String.hostOf() = substringAfter("://").substringBefore('/').substringBefore('?')
}

@Composable
fun rememberWebViewLauncher(): WebViewLauncher {
    val scope = rememberCoroutineScope()
    return remember { WebViewLauncher(Injekt.get(), scope, Injekt.get()) }
}

/** The dialogs of [launcher]: a progress dialog while an embedded browser is open, the manual cookie dialog. */
@Composable
fun WebViewDialogs(launcher: WebViewLauncher) {
    when (val state = launcher.state) {
        WebViewLauncher.State.Idle -> {}
        WebViewLauncher.State.Running -> AlertDialog(
            onDismissRequest = launcher::cancel,
            modifier = Modifier.testTag("webview-running"),
            title = { Text(stringResource(MR.strings.action_open_in_web_view)) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(stringResource(MR.strings.loading))
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = launcher::cancel, modifier = Modifier.testTag("webview-cancel")) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
        is WebViewLauncher.State.Manual -> ManualCookieDialog(state, launcher)
    }
}

@Composable
private fun ManualCookieDialog(state: WebViewLauncher.State.Manual, launcher: WebViewLauncher) {
    val entry = state.entry
    var cookies by remember(entry.url) { mutableStateOf("") }
    var userAgent by remember(entry.url, entry.userAgent) { mutableStateOf(entry.userAgent) }
    var alsoDefault by remember(entry.url) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = launcher::dismiss,
        modifier = Modifier.testTag("webview-manual"),
        title = { Text(stringResource(KMR.strings.desktop_webview_manual_title, entry.host)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!entry.browserOpened) {
                    Text(stringResource(KMR.strings.desktop_browser_open_failed))
                    SelectionContainer { Text(entry.url, modifier = Modifier.testTag("webview-url")) }
                }
                Text(stringResource(KMR.strings.desktop_webview_manual_help), style = MaterialTheme.typography.bodySmall)
                if (entry.storedCookies.isNotEmpty()) {
                    Text(
                        stringResource(KMR.strings.desktop_webview_stored_cookies, entry.storedCookies.joinToString()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("webview-stored-cookies"),
                    )
                }
                OutlinedTextField(
                    value = cookies,
                    onValueChange = { cookies = it },
                    label = { Text(stringResource(KMR.strings.desktop_webview_cookies_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("webview-cookies"),
                )
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text(stringResource(MR.strings.pref_user_agent_string)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("webview-user-agent"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = alsoDefault,
                        onCheckedChange = { alsoDefault = it },
                        modifier = Modifier.testTag("webview-user-agent-default"),
                    )
                    Text(stringResource(KMR.strings.desktop_webview_user_agent_default), style = MaterialTheme.typography.bodySmall)
                }
                when (val error = state.error) {
                    is DesktopWebView.SaveResult.InvalidCookie -> stringResource(KMR.strings.desktop_webview_cookie_invalid, error.entry)
                    DesktopWebView.SaveResult.UserAgentInvalid -> stringResource(MR.strings.error_user_agent_string_invalid)
                    else -> null
                }?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("webview-error"))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = launcher::reopenBrowser) { Text(stringResource(MR.strings.action_open_in_browser)) }
                    OutlinedButton(onClick = launcher::clearCookies, modifier = Modifier.testTag("webview-clear")) {
                        Text(stringResource(MR.strings.pref_clear_cookies))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { launcher.save(cookies, userAgent, alsoDefault) }, modifier = Modifier.testTag("webview-save")) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = { TextButton(onClick = launcher::dismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}
