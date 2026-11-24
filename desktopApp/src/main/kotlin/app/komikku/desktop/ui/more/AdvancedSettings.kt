package app.komikku.desktop.ui.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import app.komikku.desktop.log.DesktopLogFile
import app.komikku.desktop.platform.DesktopBrowser
import app.komikku.desktop.source.DesktopDelegatedSources
import app.komikku.network.browser.CloudflareHosts
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.util.system.toast
import exh.log.EHLogLevel
import exh.pref.DelegateSourcePreferences
import exh.source.ExhPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import okhttp3.Headers
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as localized

/**
 * KMK: desktop Settings → Advanced (P12), Android `SettingsAdvancedScreen` in its order with what exists on the desktop:
 * the log ("Open the log folder" for Android's "Dump crash logs", SY's log level applied at once), Data (re-index
 * downloads, clear database), Networking (clear cookies, the default user agent and its reset, the user agents kept per
 * site by the Cloudflare flows of P7 / P8, the FlareSolverr solver of P8) and SY's Developer tools (delegated sources,
 * moved here from Browse like on Android). Not on the desktop: "Clear WebView data" (no embedded browser; pasted
 * cookies go with "Clear cookies", the per-site user agents are listed), DNS over HTTPS (not wired on the desktop's
 * client), notifications / battery / onboarding / debug info, hardware bitmaps, extension installers, data saver.
 */
@Composable
internal fun advancedPreferences(navigator: Navigator): List<Preference> {
    val context = LocalContext.current
    val exhPreferences = remember { Injekt.get<ExhPreferences>() }
    val logFolder = DesktopLogFile.current?.directory ?: DesktopLogFile.directoryFor(context.dataDir)
    val folderNotOpened = stringResource(KMR.strings.desktop_folder_not_opened, logFolder.absolutePath)
    return listOf(
        Preference.PreferenceItem.TextPreference(
            title = stringResource(KMR.strings.desktop_open_log_folder),
            subtitle = stringResource(KMR.strings.desktop_open_log_folder_summary, logFolder.absolutePath),
            onClick = { if (!DesktopBrowser.openFolder(logFolder)) context.toast(folderNotOpened) },
        ),
        // SY's log level (Android: Developer tools; Komikku's verbose logging). The desktop applies it at once.
        Preference.PreferenceItem.ListPreference(
            preference = exhPreferences.logLevel(BuildConfig.DEBUG),
            entries = EHLogLevel.entries.mapIndexed { index, level ->
                index to "${context.localized(level.nameRes)} (${context.localized(level.description)})"
            }.toMap().toImmutableMap(),
            title = stringResource(SYMR.strings.log_level),
            onValueChanged = {
                DesktopLogFile.applyLevel(it)
                true
            },
        ),
        dataGroup(navigator),
        networkGroup(),
        cloudflarePreferences(),
        developerToolsGroup(),
    )
}

/** Android `getDataGroup`: re-index downloads, clear database. */
@Composable
private fun dataGroup(navigator: Navigator): Preference.PreferenceGroup {
    val context = LocalContext.current
    return Preference.PreferenceGroup(
        title = stringResource(MR.strings.label_data),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_invalidate_download_cache),
                subtitle = stringResource(MR.strings.pref_invalidate_download_cache_summary),
                onClick = {
                    Injekt.get<DownloadCache>().invalidateCache()
                    context.toast(MR.strings.download_cache_invalidated)
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_clear_database),
                subtitle = stringResource(MR.strings.pref_clear_database_summary),
                onClick = { navigator.push(ClearDatabaseScreen()) },
            ),
        ),
    )
}

/**
 * Android `getNetworkGroup` without WebView data / DNS over HTTPS: clear cookies, the default user agent (validated like
 * Android, used by extension sources after a restart) and its reset; then the per-site user agents.
 */
@Composable
private fun networkGroup(): Preference.PreferenceGroup {
    val context = LocalContext.current
    val networkPreferences = remember { Injekt.get<NetworkPreferences>() }
    val userAgentPref = remember { networkPreferences.defaultUserAgent() }
    val userAgent by userAgentPref.collectAsState()
    // The overrides are no flow: re-read them after each change made here
    var overridesVersion by remember { mutableIntStateOf(0) }
    val overrides = remember(overridesVersion) { CloudflareHosts.state.userAgentOverrides().toSortedMap() }
    val removeLabel = stringResource(MR.strings.action_remove)
    val items = buildList {
        add(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_clear_cookies),
                onClick = {
                    Injekt.get<NetworkHelper>().cookieJar.removeAll()
                    context.toast(MR.strings.cookies_cleared)
                },
            ),
        )
        add(
            Preference.PreferenceItem.EditTextPreference(
                preference = userAgentPref,
                title = stringResource(MR.strings.pref_user_agent_string),
                // The widget formats the subtitle with the value
                subtitle = userAgent.replace("%", "%%"),
                onValueChanged = {
                    try {
                        // OkHttp checks for valid values internally
                        Headers.Builder().add("User-Agent", it)
                        context.toast(MR.strings.requires_app_restart)
                        true
                    } catch (_: IllegalArgumentException) {
                        context.toast(MR.strings.error_user_agent_string_invalid)
                        false
                    }
                },
            ),
        )
        add(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_reset_user_agent_string),
                enabled = userAgent != userAgentPref.defaultValue(),
                onClick = {
                    userAgentPref.delete()
                    context.toast(MR.strings.requires_app_restart)
                },
            ),
        )
        add(Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.desktop_site_user_agents_info)))
        if (overrides.isEmpty()) {
            add(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(KMR.strings.desktop_site_user_agents),
                    // Not clickable (a disabled item would be hidden by the shared widgets)
                    subtitle = stringResource(KMR.strings.desktop_site_user_agents_empty),
                ),
            )
        }
        overrides.forEach { (host, override) ->
            add(
                Preference.PreferenceItem.TextPreference(
                    title = host,
                    subtitle = override.userAgent,
                    widget = {
                        IconButton(
                            onClick = {
                                CloudflareHosts.state.removeUserAgent(host)
                                overridesVersion++
                            },
                            modifier = Modifier.testTag("site-user-agent-remove-$host"),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = removeLabel)
                        }
                    },
                ),
            )
        }
        if (overrides.isNotEmpty()) {
            add(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(KMR.strings.desktop_clear_site_user_agents),
                    onClick = {
                        CloudflareHosts.state.clearUserAgents()
                        overridesVersion++
                        context.toast(KMR.strings.desktop_site_user_agents_cleared)
                    },
                ),
            )
        }
    }
    return Preference.PreferenceGroup(title = stringResource(MR.strings.label_network), preferenceItems = items.toImmutableList())
}

/** SY's Developer tools group, with what the desktop has: the delegated sources toggle (P4, moved from Browse). */
@Composable
private fun developerToolsGroup(): Preference.PreferenceGroup {
    val delegates = remember { Injekt.get<DelegateSourcePreferences>() }
    return Preference.PreferenceGroup(
        title = stringResource(SYMR.strings.developer_tools),
        preferenceItems = persistentListOf(
            Preference.PreferenceItem.SwitchPreference(
                delegates.delegateSources(),
                stringResource(SYMR.strings.toggle_delegated_sources),
                subtitle = stringResource(
                    SYMR.strings.toggle_delegated_sources_summary,
                    stringResource(MR.strings.app_name),
                    DesktopDelegatedSources.delegates.map { it.sourceName }.distinct().joinToString(),
                ),
            ),
        ),
    )
}
