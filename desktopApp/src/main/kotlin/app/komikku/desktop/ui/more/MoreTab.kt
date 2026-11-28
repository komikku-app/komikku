package app.komikku.desktop.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import app.komikku.desktop.ui.download.DownloadQueueScreen
import app.komikku.desktop.ui.navigation.RegisteredNavigatorHost
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.runtime.collectAsState as collectFlowAsState

/**
 * More destination (Android `MoreTab`): its own navigator starting at [MoreHomeScreen] — the
 * "downloaded only" and incognito toggles (same `BasePreferences` as Android), the download queue, data and
 * storage (backups), tracking, categories, settings and about.
 */
data object MoreTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 4u,
            title = stringResource(MR.strings.label_more),
            icon = rememberVectorPainter(Icons.Outlined.MoreHoriz),
        )

    @Composable
    override fun Content() {
        RegisteredNavigatorHost(screen = MoreHomeScreen)
    }
}

data object MoreHomeScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val basePreferences = Injekt.get<BasePreferences>()
        val downloadManager = Injekt.get<DownloadManager>()
        val downloadedOnly by basePreferences.downloadedOnly().collectAsState()
        val incognito by basePreferences.incognitoMode().collectAsState()
        val queue by downloadManager.queueState.collectFlowAsState()

        LazyColumn(Modifier.fillMaxSize().testTag("more-list")) {
            item {
                SwitchRow(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(MR.strings.label_downloaded_only),
                    subtitle = stringResource(MR.strings.downloaded_only_summary),
                    checked = downloadedOnly,
                    tag = "more-downloaded-only",
                    onChange = { basePreferences.downloadedOnly().set(it) },
                )
            }
            item {
                SwitchRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = stringResource(MR.strings.pref_incognito_mode),
                    subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
                    checked = incognito,
                    tag = "more-incognito",
                    onChange = { basePreferences.incognitoMode().set(it) },
                )
            }
            item { HorizontalDivider() }
            item {
                LinkRow(
                    icon = Icons.Outlined.GetApp,
                    title = stringResource(MR.strings.label_download_queue),
                    subtitle = queue.size.takeIf { it > 0 }?.let { pluralStringResource(MR.plurals.download_queue_summary, it, it) },
                    tag = "more-download-queue",
                    onClick = { navigator.push(DownloadQueueScreen()) },
                )
            }
            item {
                LinkRow(
                    icon = Icons.Outlined.Storage,
                    title = stringResource(MR.strings.label_data_storage),
                    subtitle = null,
                    tag = "more-data-storage",
                    onClick = { navigator.push(DataStorageScreen()) },
                )
            }
            item {
                LinkRow(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(MR.strings.pref_category_tracking),
                    subtitle = null,
                    tag = "more-tracking",
                    onClick = { navigator.push(TrackingSettingsScreen()) },
                )
            }
            item {
                LinkRow(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    title = stringResource(MR.strings.categories),
                    subtitle = null,
                    tag = "more-categories",
                    onClick = { navigator.push(CategoriesScreen()) },
                )
            }
            item { HorizontalDivider() }
            item {
                LinkRow(
                    icon = Icons.Outlined.Settings,
                    title = stringResource(MR.strings.label_settings),
                    subtitle = null,
                    tag = "more-settings",
                    onClick = { navigator.push(SettingsHomeScreen()) },
                )
            }
            item {
                LinkRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(MR.strings.pref_category_about),
                    subtitle = BuildConfig.VERSION_NAME,
                    tag = "more-about",
                    onClick = { navigator.push(SettingsCategoryScreen(SettingsCategory.About)) },
                )
            }
        }
    }
}

@Composable
fun SwitchRow(icon: ImageVector?, title: String, subtitle: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) }.testTag(tag),
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
fun LinkRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.5f).testTag(tag),
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
    )
}
