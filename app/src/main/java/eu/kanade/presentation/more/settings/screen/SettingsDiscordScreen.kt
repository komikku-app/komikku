// AM (DISCORD) -->
package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastMap
import eu.kanade.domain.connection.service.ConnectionPreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDiscordScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_category_connections

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri("https://tachiyomi.org/help/guides/tracking/") }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(R.string.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val connectionPreferences = remember { Injekt.get<ConnectionPreferences>() }
        val connectionsManager = remember { Injekt.get<ConnectionsManager>() }
        val enableDRPCPref = connectionPreferences.enableDiscordRPC()
        val useChapterTitlesPref = connectionPreferences.useChapterTitles()
        val discordRPCStatus = connectionPreferences.discordRPCStatus()

        val enableDRPC by enableDRPCPref.collectAsState()
        val useChapterTitles by useChapterTitlesPref.collectAsState()

        var dialog by remember { mutableStateOf<Any?>(null) }
        dialog?.run {
            when (this) {
                is LogoutConnectionDialog -> {
                    ConnectionsLogoutDialog(
                        service = service,
                        onDismissRequest = {
                            dialog = null
                            enableDRPCPref.set(false)
                        },
                    )
                }
            }
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(R.string.connections_discord),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = enableDRPCPref,
                        title = stringResource(R.string.pref_enable_discord_rpc),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = useChapterTitlesPref,
                        enabled = enableDRPC,
                        title = stringResource(id = R.string.show_chapters_titles_title),
                        subtitle = stringResource(id = R.string.show_chapters_titles_subtitle),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = discordRPCStatus,
                        title = stringResource(R.string.pref_discord_status),
                        entries = persistentMapOf(
                            -1 to stringResource(R.string.pref_discord_dnd),
                            0 to stringResource(R.string.pref_discord_idle),
                            1 to stringResource(R.string.pref_discord_online),
                        ),
                        enabled = enableDRPC,
                    ),
                ),
            ),
            getRPCIncognitoGroup(
                connectionPreferences = connectionPreferences,
                enabled = enableDRPC,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.logout),
                onClick = { dialog = LogoutConnectionDialog(connectionsManager.discord) },
            ),
        )
    }

    @Composable
    private fun getRPCIncognitoGroup(
        connectionPreferences: ConnectionPreferences,
        enabled: Boolean,
    ): Preference.PreferenceGroup {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val allCategories by getCategories.subscribe().collectAsState(
            initial = runBlocking { getCategories.await() },
        )

        val discordRPCIncognitoPref = connectionPreferences.discordRPCIncognito()
        val discordRPCIncognitoCategoriesPref = connectionPreferences.discordRPCIncognitoCategories()

        val includedManga by discordRPCIncognitoCategoriesPref.collectAsState()
        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            TriStateListDialog(
                title = stringResource(R.string.categories),
                message = stringResource(R.string.pref_discord_incognito_categories_details),
                items = allCategories,
                initialChecked = includedManga.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                initialInversed = includedManga.mapNotNull { allCategories.find { false } },
                itemLabel = { it.visualName },
                onDismissRequest = { showDialog = false },
                onValueChanged = { newIncluded, _ ->
                    discordRPCIncognitoCategoriesPref.set(
                        newIncluded.fastMap { it.id.toString() }
                            .toSet(),
                    )
                    showDialog = false
                },
                onlyChecked = true,
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(R.string.categories),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = discordRPCIncognitoPref,
                    title = stringResource(R.string.pref_discord_incognito),
                    subtitle = stringResource(R.string.pref_discord_incognito_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = includedManga,
                    ),
                    onClick = { showDialog = true },
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(R.string.pref_discord_incognito_categories_details),
                ),
            ),
            enabled = enabled,
        )
    }
}
// <-- AM (DISCORD)
