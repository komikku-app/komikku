package eu.kanade.presentation.webview

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import io.github.edsuns.adfilter.Filter
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AdFilterSettings(
    filters: LinkedHashMap<String, Filter>,
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
                        items = filters.values.toList(),
                        key = { "adblock-filters-${it.id}" },
                    ) { filter ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = MaterialTheme.padding.small),
                            ) {
                                Text(
                                    text = filter.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
//                                    Text(
//                                        text = filter.url,
//                                        style = MaterialTheme.typography.bodySmall,
//                                        color = LocalContentColor.current.copy(alpha = 0.75f),
//                                        maxLines = 1,
//                                        overflow = TextOverflow.Ellipsis,
//                                        modifier = Modifier.basicMarquee(
//                                            repeatDelayMillis = 2_000,
//                                        ),
//                                    )
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (filter.downloadState.isRunning) {
                                            filter.downloadState.toString()
                                        } else {
                                            filter.updateTime.toString()
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalContentColor.current.copy(alpha = 0.75f),
                                    )
                                    Text(
                                        text = filter.filtersCount.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalContentColor.current.copy(alpha = 0.75f),
                                    )
                                }
                            }

                            Switch(
                                checked = filter.isEnabled,
                                onCheckedChange = {
                                    filterSwitch(filter.id, it)
                                },
                                modifier = Modifier.padding(start = TrailingWidgetBuffer),
                            )
                        }
                    }
                }
            }
        }
    }
}
