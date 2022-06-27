package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ChipBorder
import androidx.compose.material3.LocalMinimumTouchTargetEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowRow
import eu.kanade.presentation.components.SuggestionChip
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.util.SourceTagsUtil
import tachiyomi.source.Source

@Immutable
data class DisplayTag(
    val namespace: String?,
    val text: String,
    val search: String,
    val border: Int?,
)

@Immutable
@JvmInline
value class SearchMetadataChips(
    val tags: Map<String, List<DisplayTag>>,
) {
    companion object {
        operator fun invoke(meta: RaisedSearchMetadata?, source: Source, tags: List<String>?): SearchMetadataChips? {
            return if (meta != null) {
                SearchMetadataChips(
                    meta.tags
                        .filterNot { it.type == RaisedSearchMetadata.TAG_TYPE_VIRTUAL }
                        .map {
                            DisplayTag(
                                namespace = it.namespace,
                                text = it.name,
                                search = if (!it.namespace.isNullOrEmpty()) {
                                    SourceTagsUtil.getWrappedTag(source.id, namespace = it.namespace, tag = it.name)
                                } else {
                                    SourceTagsUtil.getWrappedTag(source.id, fullTag = it.name)
                                } ?: it.name,
                                border = if (source.id == EXH_SOURCE_ID || source.id == EH_SOURCE_ID) {
                                    when (it.type) {
                                        EHentaiSearchMetadata.TAG_TYPE_NORMAL -> 3
                                        EHentaiSearchMetadata.TAG_TYPE_LIGHT -> 1
                                        else -> null
                                    }
                                } else null,
                            )
                        }
                        .groupBy { it.namespace.orEmpty() },
                )
            } else if (tags != null && tags.all { it.contains(':') }) {
                SearchMetadataChips(
                    tags
                        .map { tag ->
                            val index = tag.indexOf(':')
                            DisplayTag(tag.substring(0, index).trim(), tag.substring(index + 1).trim(), tag, null)
                        }
                        .groupBy {
                            it.namespace.orEmpty()
                        },
                )
            } else null
        }
    }
}

@Composable
fun NamespaceTags(
    tags: SearchMetadataChips,
    onClick: (item: String) -> Unit,
    onLongClick: (item: String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.tags.forEach { (namespace, tags) ->
            Row(Modifier.padding(start = 16.dp)) {
                if (namespace.isNotEmpty()) {
                    TagsChip(namespace, onClick = null, onLongClick = null)
                }
                FlowRow(
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                    mainAxisSpacing = 4.dp,
                    crossAxisSpacing = 8.dp,
                ) {
                    tags.forEach { (_, text, search, border) ->
                        TagsChip(
                            text = text,
                            onClick = { onClick(search) },
                            onLongClick = { onLongClick(search) },
                            border = border?.let {
                                with(LocalDensity.current) {
                                    SuggestionChipDefaults.suggestionChipBorder(borderWidth = it.toDp())
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TagsChip(
    text: String,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    border: ChipBorder? = null,
) {
    CompositionLocalProvider(LocalMinimumTouchTargetEnforcement provides false) {
        if (onClick != null) {
            if (onLongClick != null) {
                SuggestionChip(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    label = { Text(text = text, style = MaterialTheme.typography.bodySmall) },
                    border = border,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            } else {
                SuggestionChip(
                    onClick = onClick,
                    label = { Text(text = text, style = MaterialTheme.typography.bodySmall) },
                    border = border,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        } else {
            SuggestionChip(
                label = { Text(text = text, style = MaterialTheme.typography.bodySmall) },
                border = border,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Preview
@Composable
fun NamespaceTagsPreview() {
    TachiyomiTheme {
        Surface {
            val context = LocalContext.current
            NamespaceTags(
                tags = remember {
                    EHentaiSearchMetadata().apply {
                        this.tags.addAll(
                            arrayOf(
                                RaisedTag(
                                    "Male",
                                    "Test",
                                    EHentaiSearchMetadata.TAG_TYPE_NORMAL,
                                ),
                                RaisedTag(
                                    "Male",
                                    "Test2",
                                    EHentaiSearchMetadata.TAG_TYPE_WEAK,
                                ),
                                RaisedTag(
                                    "Male",
                                    "Test3",
                                    EHentaiSearchMetadata.TAG_TYPE_LIGHT,
                                ),
                                RaisedTag(
                                    "Female",
                                    "Test",
                                    EHentaiSearchMetadata.TAG_TYPE_NORMAL,
                                ),
                                RaisedTag(
                                    "Female",
                                    "Test2",
                                    EHentaiSearchMetadata.TAG_TYPE_WEAK,
                                ),
                                RaisedTag(
                                    "Female",
                                    "Test3",
                                    EHentaiSearchMetadata.TAG_TYPE_LIGHT,
                                ),
                            ),
                        )
                    }.let { SearchMetadataChips(it, EHentai(EXH_SOURCE_ID, true, context), emptyList()) }!!
                },
                onClick = {},
                onLongClick = {},
            )
        }
    }
}
