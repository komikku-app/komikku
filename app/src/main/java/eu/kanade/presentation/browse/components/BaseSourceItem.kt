package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.extension.interactor.GetExtensionLanguages.Companion.getLanguageIconID
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun BaseSourceItem(
    source: Source,
    modifier: Modifier = Modifier,
    showLanguageInContent: Boolean = true,
    onClickItem: () -> Unit = {},
    onLongClickItem: () -> Unit = {},
    icon: @Composable RowScope.(Source) -> Unit = defaultIcon,
    action: @Composable RowScope.(Source) -> Unit = {},
    content: @Composable RowScope.(Source, String?, /* KMK --> */ String /* KMK <-- */) -> Unit = defaultContent,
) {
    val sourceLangString = LocaleHelper.getSourceDisplayName(source.lang, LocalContext.current).takeIf {
        showLanguageInContent
    }
    BaseBrowseItem(
        modifier = modifier,
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        icon = { icon.invoke(this, source) },
        action = { action.invoke(this, source) },
        content = { content.invoke(this, source, sourceLangString, /* KMK --> */ source.lang /* KMK <-- */) },
    )
}

private val defaultIcon: @Composable RowScope.(Source) -> Unit = { source ->
    SourceIcon(source = source)
}

private val defaultContent: @Composable RowScope.(
    Source,
    String?,
    // KMK -->
    String,
    // KMK <--
) -> Unit = { source, sourceLangString, /* KMK --> */ lang /* KMK <-- */ ->
    Column(
        modifier = Modifier
            .padding(horizontal = MaterialTheme.padding.medium)
            .weight(1f),
    ) {
        Text(
            text = source.name +
                // KMK -->
                (
                    source.installedExtension?.let { extension ->
                        " (${extension.name})".takeIf { extension.name != source.name }
                    } ?: ""
                    ),
            // KMK <--
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        // KMK -->
        // Won't look good but it's not like we can ellipsize overflowing content
        FlowRow(
            modifier = Modifier.secondaryItemAlpha(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                // KMK <--
                if (sourceLangString != null) {
                    // KMK -->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val iconResId = getLanguageIconID(lang) ?: R.drawable.globe
                        Icon(
                            painter = painterResource(id = iconResId),
                            tint = Color.Unspecified,
                            contentDescription = lang,
                            modifier = Modifier
                                .width(18.dp)
                                .height(12.dp)
                        )
                        // KMK <--
                        Text(
                            modifier = Modifier.secondaryItemAlpha(),
                            text = sourceLangString,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // KMK -->
                if (source.installedExtension?.isNsfw == true) {
                    Text(
                        text = stringResource(MR.strings.ext_nsfw_short).uppercase(),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // KMK <--
    }
}
