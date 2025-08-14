// AM (DISCORD) -->
package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDiscordScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsDiscordScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_category_connections

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri("https://tachiyomi.org/help/guides/tracking/") }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        val connectionsPreferences = remember { Injekt.get<ConnectionsPreferences>() }
        val connectionsManager = remember { Injekt.get<ConnectionsManager>() }
        val enableDRPCPref = connectionsPreferences.enableDiscordRPC()
        val useChapterTitlesPref = connectionsPreferences.useChapterTitles()
        val discordRPCStatus = connectionsPreferences.discordRPCStatus()
        val customMessagePref = connectionsPreferences.discordCustomMessage()
        val showProgressPref = connectionsPreferences.discordShowProgress()
        val showButtonsPref = connectionsPreferences.discordShowButtons()
        val showDownloadButtonPref = connectionsPreferences.discordShowDownloadButton()
        val showDiscordButtonPref = connectionsPreferences.discordShowDiscordButton()

        val enableDRPC by enableDRPCPref.collectAsState()
        val showButtons by showButtonsPref.collectAsState()

        var dialog by remember { mutableStateOf<Any?>(null) }
        dialog?.run {
            when (this) {
                is LogoutConnectionDialog -> {
                    ConnectionsLogoutDialog(
                        // KMK -->
                        serviceName = stringResource(service.nameStrRes()),
                        onConfirmation = {
                            enableDRPCPref.set(false)
                            service.logout()
                            navigator.pop()
                        },
                        // KMK <--
                        onDismissRequest = {
                            dialog = null
                        },
                    )
                }
            }
        }

        var showCustomMessageDialog by rememberSaveable { mutableStateOf(false) }
        var tempCustomMessage by rememberSaveable { mutableStateOf(customMessagePref.get()) }

        if (showCustomMessageDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCustomMessageDialog = false
                    tempCustomMessage = customMessagePref.get()
                },
                title = { Text(stringResource(KMR.strings.pref_discord_custom_message)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = tempCustomMessage,
                            onValueChange = { tempCustomMessage = it },
                            label = { Text(stringResource(KMR.strings.pref_discord_custom_message_summary)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        TextButton(
                            onClick = {
                                customMessagePref.delete()
                                tempCustomMessage = ""
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(MR.strings.action_reset))
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            customMessagePref.set(tempCustomMessage)
                            showCustomMessageDialog = false
                        },
                    ) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCustomMessageDialog = false
                            tempCustomMessage = customMessagePref.get()
                        },
                    ) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(KMR.strings.discord_accounts),
                onClick = { navigator.push(DiscordAccountsScreen) },
            ),
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.connections_discord),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = enableDRPCPref,
                        title = stringResource(KMR.strings.pref_enable_discord_rpc),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = useChapterTitlesPref,
                        title = stringResource(KMR.strings.show_chapters_titles_title),
                        subtitle = stringResource(KMR.strings.show_chapters_titles_subtitle),
                        enabled = enableDRPC,
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discordRPCStatus,
                        title = stringResource(KMR.strings.pref_discord_status),
                        entries = persistentMapOf(
                            -1 to stringResource(KMR.strings.pref_discord_dnd),
                            0 to stringResource(KMR.strings.pref_discord_idle),
                            1 to stringResource(KMR.strings.pref_discord_online),
                        ),
                    ),
                ),
            ),
            getRPCIncognitoGroup(
                connectionsPreferences = connectionsPreferences,
                enabled = enableDRPC,
            ),
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.pref_category_discord_customization),
                enabled = enableDRPC,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.pref_discord_custom_message),
                        subtitle = stringResource(KMR.strings.pref_discord_custom_message_summary),
                        onClick = { showCustomMessageDialog = true },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showProgressPref,
                        title = stringResource(KMR.strings.pref_discord_show_progress),
                        subtitle = stringResource(KMR.strings.pref_discord_show_progress_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showButtonsPref,
                        title = stringResource(KMR.strings.pref_discord_show_buttons),
                        subtitle = stringResource(KMR.strings.pref_discord_show_buttons_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showDownloadButtonPref,
                        title = stringResource(KMR.strings.pref_discord_show_download_button),
                        subtitle = stringResource(KMR.strings.pref_discord_show_download_button_summary),
                        enabled = showButtons,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = showDiscordButtonPref,
                        title = stringResource(KMR.strings.pref_discord_show_discord_button),
                        subtitle = stringResource(KMR.strings.pref_discord_show_discord_button_summary),
                        enabled = showButtons,
                    ),
                ),
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.logout),
                onClick = { dialog = LogoutConnectionDialog(connectionsManager.discord) },
            ),
        )
    }

    @Composable
    private fun getRPCIncognitoGroup(
        connectionsPreferences: ConnectionsPreferences,
        enabled: Boolean,
    ): Preference.PreferenceGroup {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val allCategories by getCategories.subscribe().collectAsState(
            initial = runBlocking { getCategories.await() },
        )

        val discordRPCIncognitoPref = connectionsPreferences.discordRPCIncognito()
        val discordRPCIncognitoCategoriesPref = connectionsPreferences.discordRPCIncognitoCategories()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.categories),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = discordRPCIncognitoPref,
                    title = stringResource(KMR.strings.pref_discord_incognito),
                    subtitle = stringResource(KMR.strings.pref_discord_incognito_summary),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = discordRPCIncognitoCategoriesPref,
                    entries = allCategories
                        .associate { it.id.toString() to it.visualName }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.categories),
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(KMR.strings.pref_discord_incognito_categories_details),
                ),
            ),
            enabled = enabled,
        )
    }
}
// <-- AM (DISCORD)
