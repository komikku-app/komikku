package app.komikku.desktop.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.komikku.desktop.webview.DesktopWebView
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.extension.interactor.ExtensionSourceItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsEvent
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogD
import exh.log.xLogE
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * P22: Android's extension info (`ExtensionDetailsScreen`) on the borrowed [ExtensionDetailsScreenModel]: the redundant /
 * obsolete warnings, name, package, version, language and age rating (NSFW → Android's warning), Uninstall (the screen
 * closes once the extension is gone), the extension's incognito switch, its sources with their enable switches and
 * settings; enable all / disable all / clear cookies in the top bar.
 *
 * Not on desktop: Android's "App info" (system settings of a shared extension) and "Open in WebView" / "Open repository"
 * (the desktop opens a source's site from its catalogue).
 */
class ExtensionDetailsScreen(internal val pkgName: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val model = rememberScreenModel { ExtensionDetailsScreenModel(pkgName, context) }
        val state by model.state.collectAsState()

        LaunchedEffect(model) {
            model.events.collect { event ->
                if (event is ExtensionDetailsEvent.Uninstalled) navigator.pop()
            }
        }

        BrowseScaffold(
            title = stringResource(MR.strings.label_extension_info),
            onBack = navigator::pop,
            actions = {
                if (state.extension != null) {
                    IconButton(onClick = { model.toggleSources(true) }, modifier = Modifier.testTag("extension-details-enable-all")) {
                        Icon(Icons.Outlined.DoneAll, contentDescription = stringResource(MR.strings.action_enable_all))
                    }
                    IconButton(onClick = { model.toggleSources(false) }, modifier = Modifier.testTag("extension-details-disable-all")) {
                        Icon(Icons.Outlined.RemoveDone, contentDescription = stringResource(MR.strings.action_disable_all))
                    }
                    IconButton(
                        // P22 review: the desktop's own clearing (whatever the cookies' domain), on IO, with a
                        // message; not the borrowed model's `clearCookies` (it expires only host-only, default-path cookies)
                        onClick = {
                            val extension = state.extension ?: return@IconButton
                            model.screenModelScope.launchIO {
                                ExtensionCookies.clear(extension)
                                withUIContext { context.toast(MR.strings.cookies_cleared) }
                            }
                        },
                        modifier = Modifier.testTag("extension-details-clear-cookies"),
                    ) {
                        Icon(Icons.Outlined.Cookie, contentDescription = stringResource(MR.strings.pref_clear_cookies))
                    }
                }
            },
        ) {
            val extension = state.extension
            when {
                state.isLoading || extension == null -> LoadingScreen()
                else -> ExtensionDetails(
                    extension = extension,
                    sources = state.sources,
                    incognito = state.isIncognito,
                    onUninstall = model::uninstallExtension,
                    onIncognito = model::toggleIncognito,
                    onToggleSource = model::toggleSource,
                    onSourceSettings = { navigator.push(SourcePreferencesScreen(it)) },
                )
            }
        }
    }
}

@Composable
private fun ExtensionDetails(
    extension: Extension.Installed,
    sources: List<ExtensionSourceItem>,
    incognito: Boolean,
    onUninstall: () -> Unit,
    onIncognito: (Boolean) -> Unit,
    onToggleSource: (Long) -> Unit,
    onSourceSettings: (Long) -> Unit,
) {
    val context = LocalContext.current
    var nsfwWarning by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("extension-details")) {
        if (extension.isRedundant) item(key = "redundant") { Warning(stringResource(SYMR.strings.redundant_extension_message)) }
        if (extension.isObsolete) item(key = "obsolete") { Warning(stringResource(MR.strings.obsolete_extension_message)) }
        item(key = "header") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExtensionIcon(extension)
                    Column {
                        Text(extension.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            extension.pkgName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("extension-details-package"),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    InfoText(extension.versionName, stringResource(MR.strings.ext_info_version))
                    InfoText(
                        LocaleHelper.getSourceDisplayName(extension.lang, context),
                        stringResource(MR.strings.ext_info_language),
                    )
                    if (extension.isNsfw) {
                        InfoText(
                            stringResource(MR.strings.ext_nsfw_short),
                            stringResource(MR.strings.ext_info_age_rating) + " ⓘ",
                            modifier = Modifier.clickable { nsfwWarning = true }.testTag("extension-details-nsfw"),
                        )
                    }
                }
                OutlinedButton(onClick = { confirmUninstall = true }, modifier = Modifier.testTag("extension-details-uninstall")) {
                    Text(stringResource(MR.strings.ext_uninstall))
                }
            }
        }
        item(key = "incognito") {
            ListItem(
                headlineContent = { Text(stringResource(MR.strings.pref_incognito_mode)) },
                supportingContent = { Text(stringResource(MR.strings.pref_incognito_mode_extension_summary)) },
                trailingContent = {
                    Switch(
                        checked = incognito,
                        onCheckedChange = onIncognito,
                        modifier = Modifier.testTag("extension-details-incognito"),
                    )
                },
            )
            HorizontalDivider()
        }
        items(sources, key = { "extension-details-${it.source.id}" }) { item ->
            val source = item.source
            ListItem(
                modifier = Modifier.clickable { onToggleSource(source.id) }.testTag("extension-details-source-${source.id}"),
                headlineContent = {
                    Text(
                        if (item.labelAsName) source.name else LocaleHelper.getSourceDisplayName(source.lang, context),
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (source is ConfigurableSource) {
                            IconButton(onClick = { onSourceSettings(source.id) }) {
                                Icon(Icons.Outlined.Settings, contentDescription = stringResource(MR.strings.label_settings))
                            }
                        }
                        Switch(
                            checked = item.enabled,
                            onCheckedChange = { onToggleSource(source.id) },
                            modifier = Modifier.testTag("extension-details-source-switch-${source.id}"),
                        )
                    }
                },
            )
        }
    }
    if (confirmUninstall) {
        ExtensionUninstallDialog(
            name = extension.name,
            onConfirm = {
                confirmUninstall = false
                onUninstall()
            },
            onDismiss = { confirmUninstall = false },
        )
    }
    if (nsfwWarning) {
        AlertDialog(
            text = { Text(stringResource(MR.strings.ext_nsfw_warning)) },
            confirmButton = { TextButton(onClick = { nsfwWarning = false }) { Text(stringResource(MR.strings.action_ok)) } },
            onDismissRequest = { nsfwWarning = false },
        )
    }
}

/**
 * P22 review: Android asks through the system's uninstall dialog; the desktop deletes the APK at once, so it asks here
 * (the extension info and the Extensions tab's rows).
 */
@Composable
internal fun ExtensionUninstallDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.ext_uninstall)) },
        text = { Text(stringResource(KMR.strings.js_source_uninstall_confirm, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("extension-uninstall-confirm")) {
                Text(stringResource(MR.strings.ext_uninstall))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

/**
 * P22 review: Android's "Clear cookies" of the extension info for the desktop. Each source's base and home address goes
 * through [DesktopWebView.clearCookies] (Android's WebView "Clear cookies" on desktop): every jar cookie of its host is
 * removed whatever its path (P23) and the domain cookies that apply to the host (Cloudflare's `cf_clearance` for the
 * registrable domain), together with the host's remembered solver User-Agent and failed solve; other sites' cookies
 * stay. Blocking; returns how many cookies were removed.
 */
internal object ExtensionCookies {
    fun clear(
        extension: Extension.Installed,
        webView: DesktopWebView = Injekt.get(),
    ): Int {
        val urls = extension.sources
            .filterIsInstance<HttpSource>()
            .flatMap { source -> listOf(runCatching { source.baseUrl }.getOrNull(), runCatching { source.getHomeUrl() }.getOrNull()) }
            .filterNotNull()
            .filter { it.isNotEmpty() }
            .distinct()
        val cleared = urls.sumOf { url ->
            try {
                webView.clearCookies(url)
            } catch (e: Exception) {
                xLogE("Failed to clear cookies for $url", e)
                0
            }
        }
        xLogD("Cleared $cleared cookies for: ${urls.joinToString()}")
        return cleared
    }
}

@Composable
private fun InfoText(primary: String, secondary: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(secondary, style = MaterialTheme.typography.bodySmall)
    }
}

/** Android's `WarningBanner`. */
@Composable
private fun Warning(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onError,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error).padding(16.dp),
    )
}
