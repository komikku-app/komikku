package eu.kanade.presentation.manga.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.ChipBorder
import eu.kanade.presentation.components.SuggestionChip
import eu.kanade.presentation.components.SuggestionChipDefaults
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.all.EHentai
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import exh.util.SourceTagsUtil
import androidx.compose.material3.SuggestionChipDefaults as SuggestionChipDefaultsM3

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
                                        EHentaiSearchMetadata.TAG_TYPE_NORMAL -> 2
                                        EHentaiSearchMetadata.TAG_TYPE_LIGHT -> 1
                                        else -> null
                                    }
                                } else {
                                    null
                                },
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
            } else {
                null
            }
        }
    }
}

@Composable
fun NamespaceTags(
    tags: SearchMetadataChips,
    onClick: (item: String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.tags.forEach { (namespace, tags) ->
            Row(Modifier.padding(start = 16.dp)) {
                if (namespace.isNotEmpty()) {
                    TagsChip(
                        modifier = Modifier.padding(top = 4.dp),
                        text = namespace,
                        onClick = null,
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tags.forEach { (_, text, search, border) ->
                        val borderDp = border?.dp
                        TagsChip(
                            modifier = Modifier.padding(vertical = 4.dp),
                            text = text,
                            onClick = { onClick(search) },
                            border = borderDp?.let {
                                SuggestionChipDefaults.suggestionChipBorder(borderWidth = it)
                            } ?: SuggestionChipDefaults.suggestionChipBorder(),
                            borderM3 = borderDp?.let {
                                SuggestionChipDefaultsM3.suggestionChipBorder(enabled = true, borderWidth = it)
                            } ?: SuggestionChipDefaultsM3.suggestionChipBorder(enabled = true),
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
    modifier: Modifier = Modifier,
    border: ChipBorder? = SuggestionChipDefaults.suggestionChipBorder(),
    borderM3: BorderStroke? = SuggestionChipDefaultsM3.suggestionChipBorder(enabled = true),
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        if (onClick != null) {
            SuggestionChip(
                modifier = modifier,
                onClick = onClick,
                label = {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                border = borderM3,
            )
        } else {
            SuggestionChip(
                modifier = modifier,
                label = {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                border = border,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun NamespaceTagsPreview() {
    TachiyomiPreviewTheme {
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
            )
        }
    }
}
