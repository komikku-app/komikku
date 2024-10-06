package eu.kanade.presentation.webview.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.webview.AdFilterModel
import sample.main.blocking.BlockingInfo
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.padding

@Composable
fun AdFilterLogDialog(
    adFilterModel: AdFilterModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blockingInfoMap by adFilterModel.blockingInfoMap.collectAsState()
    val currentUrl by adFilterModel.currentPageUrl.collectAsState()
    val blockingInfo: BlockingInfo? = blockingInfoMap[currentUrl]
    val blockedUrlCount = blockingInfo?.blockedUrlMap?.size ?: 0

    AdaptiveSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Blocked $blockedUrlCount connection(s)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "${blockingInfo?.blockedRequests ?: 0} time(s) blocked" +
                            blockingInfo?.let { " / ${it.allRequests} request(s)" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(
                            repeatDelayMillis = 2_000,
                        ),
                    )
                }

                IconButton(
                    onClick = {
                        adFilterModel.showFilterSettingsDialog(
                            onDismissDialog = {
                                adFilterModel.showFilterLogDialog()
                            },
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Adblock Settings",
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier
                    .padding(vertical = MaterialTheme.padding.medium),
            )

            blockingInfo?.let {
                ScrollbarLazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f),
                ) {
                    items(
                        items = blockingInfo.blockedUrlMap.toList(),
                        key = { "adblock-filter-logs-${it.first.hashCode()}" },
                    ) { blockedUrlMap ->
                        Column(
                            modifier = Modifier
                                .padding(vertical = MaterialTheme.padding.small),
                        ) {
                            Text(
                                text = blockedUrlMap.first,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = blockedUrlMap.second,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }
}
