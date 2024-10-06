package eu.kanade.presentation.webview.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import sample.main.blocking.BlockingInfo
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.padding

@Composable
fun AdFilterLogDialog(
    blockingInfo: BlockingInfo?,
    onAdBlockSettingClick: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveSheet(
        modifier = modifier
            .sizeIn(maxHeight = 500.dp),
        onDismissRequest = onDismissRequest,
    ) {
        AdFilterLogDialogContent(
            blockingInfo = blockingInfo,
            onAdBlockSettingClick = onAdBlockSettingClick,
        )
    }
}

@Composable
fun AdFilterLogDialogContent(
    blockingInfo: BlockingInfo?,
    onAdBlockSettingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blockedUrlCount = blockingInfo?.blockedUrlMap?.size ?: 0

    Column(
        modifier = modifier
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
                onClick = onAdBlockSettingClick,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Adblock Settings",
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.small),
        )

        blockingInfo?.let {
            ScrollbarLazyColumn(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                items(
                    items = blockingInfo.blockedUrlMap.toList(),
                    key = { "adblock-filter-logs-${it.first.hashCode()}" },
                ) { blockedUrlMap ->
                    Column(
                        modifier = Modifier
                            .padding(vertical = MaterialTheme.padding.extraSmall),
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

@PreviewLightDark
@Composable
private fun AdFilterLogDialogPreview() {
    TachiyomiPreviewTheme {
        Surface {
            AdFilterLogDialogContent(
                BlockingInfo(
                    allRequests = 10,
                    blockedRequests = 5,
                    blockedUrlMap = LinkedHashMap(
                        mapOf(
                            "blocking url 1" to "rule 1",
                            "blocking url 2" to "rule 2",
                        ),
                    ),
                ),
                onAdBlockSettingClick = {},
            )
        }
    }
}
