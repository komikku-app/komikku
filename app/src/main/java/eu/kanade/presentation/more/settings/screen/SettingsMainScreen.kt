package eu.kanade.presentation.more.settings.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.UnsortedPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScaffold
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import exh.assets.EhAssets
import exh.assets.ehassets.EhLogo
import exh.assets.ehassets.MangadexLogo
import exh.md.utils.MdUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsMainScreen : SearchableSettings {
    @Composable
    @ReadOnlyComposable
    override fun getTitle(): String = stringResource(id = R.string.label_settings)

    @Composable
    @NonRestartableComposable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        // SY -->
        val isHentaiEnabled by remember { Injekt.get<UnsortedPreferences>() }.isHentaiEnabled().collectAsState()
        // SY <--
        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_general),
                icon = Icons.Outlined.Tune,
                onClick = { navigator.push(SettingsGeneralScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_appearance),
                icon = Icons.Outlined.Palette,
                onClick = { navigator.push(SettingsAppearanceScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_library),
                icon = Icons.Outlined.CollectionsBookmark,
                onClick = { navigator.push(SettingsLibraryScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_reader),
                icon = Icons.Outlined.ChromeReaderMode,
                onClick = { navigator.push(SettingsReaderScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_downloads),
                icon = Icons.Outlined.GetApp,
                onClick = { navigator.push(SettingsDownloadScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_tracking),
                icon = Icons.Outlined.Sync,
                onClick = { navigator.push(SettingsTrackingScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.browse),
                icon = Icons.Outlined.Explore,
                onClick = { navigator.push(SettingsBrowseScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.label_backup),
                icon = Icons.Outlined.SettingsBackupRestore,
                onClick = { navigator.push(SettingsBackupScreen()) },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_security),
                icon = Icons.Outlined.Security,
                onClick = { navigator.push(SettingsSecurityScreen()) },
            ),
            // SY -->
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_eh),
                icon = EhAssets.EhLogo,
                onClick = { navigator.push(SettingsEhScreen()) },
                enabled = isHentaiEnabled,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_mangadex),
                icon = EhAssets.MangadexLogo,
                onClick = { navigator.push(SettingsMangadexScreen()) },
                enabled = remember { MdUtil.getEnabledMangaDexs(Injekt.get()).isNotEmpty() },
            ),
            // SY <--
            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.pref_category_advanced),
                icon = Icons.Outlined.Code,
                onClick = { navigator.push(SettingsAdvancedScreen()) },
            ),
        )
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        PreferenceScaffold(
            title = getTitle(),
            actions = {
                AppBarActions(
                    listOf(
                        AppBar.Action(
                            title = stringResource(R.string.action_search),
                            icon = Icons.Outlined.Search,
                            onClick = { navigator.push(SettingsSearchScreen()) },
                        ),
                    ),
                )
            },
            onBackPressed = backPress::invoke,
            itemsProvider = { getPreferences() },
        )
    }
}
