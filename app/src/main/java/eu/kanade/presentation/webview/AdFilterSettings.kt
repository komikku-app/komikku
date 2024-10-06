package eu.kanade.presentation.webview

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import io.github.edsuns.adfilter.DownloadState
import io.github.edsuns.adfilter.Filter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AdFilterSettings(
    filters: ImmutableList<Filter>,
    isAdblockEnabled: Boolean,
    masterFiltersSwitch: (Boolean) -> Unit,
    filterSwitch: (String, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AppBar(
                title = "AdBlock Settings",
                navigateUp = onDismissRequest,
                navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_webview_refresh),
                                icon = Icons.Outlined.Refresh,
                                onClick = {
                                    TODO()
                                },
                            ),
                            AppBar.Action(
                                title = stringResource(MR.strings.action_add),
                                icon = Icons.Outlined.Add,
                                onClick = {
                                    TODO()
                                },
                            ),
                        ),
                    )
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues),
        ) {
            SwitchPreferenceWidget(
                title = stringResource(MR.strings.pref_incognito_mode),
                subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
                checked = isAdblockEnabled,
                onCheckedChanged = {
                    masterFiltersSwitch(it)
                },
            )
            HorizontalDivider()

            if (isAdblockEnabled) {
                ScrollbarLazyColumn(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .padding(top = MaterialTheme.padding.small)
                        .fillMaxSize(),
                ) {
                    items(
                        items = filters,
                        key = { "adblock-filters-${it.id}" },
                    ) { filter ->
                        FilterSwitchItem(
                            url = filter.url,
                            name = filter.name,
                            isEnabled = filter.isEnabled,
                            downloadState = filter.downloadState,
                            updateTime = relativeDateText(filter.updateTime),
                            filtersCount = filter.filtersCount,
                            filterSwitch = { enabled ->
                                filterSwitch(filter.id, enabled)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSwitchItem(
    url: String,
    name: String,
    isEnabled: Boolean,
    downloadState: DownloadState,
    updateTime: String,
    filtersCount: Int,
    filterSwitch: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.padding.extraSmall,
                vertical = MaterialTheme.padding.small,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(
                    repeatDelayMillis = 2_000,
                ),
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (downloadState.isRunning) {
                        downloadState.toString()
                    } else {
                        updateTime
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
                Text(
                    text = filtersCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                )
            }
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = {
                filterSwitch(it)
            },
            modifier = Modifier.padding(start = TrailingWidgetBuffer),
        )
    }
}

@PreviewLightDark
@Composable
private fun FilterSwitchPreview() {
    TachiyomiPreviewTheme {
        Surface {
            FilterSwitchItem(
                url = "https://filters.adtidy.org/extension/chromium/filters/2.txt",
                name = "Adguard",
                isEnabled = true,
                downloadState = DownloadState.SUCCESS,
                updateTime = "Today",
                filtersCount = 1000,
                filterSwitch = { },
            )
        }
    }
}
