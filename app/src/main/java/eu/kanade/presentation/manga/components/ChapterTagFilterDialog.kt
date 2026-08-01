package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import tachiyomi.domain.chapterTag.model.ChapterTag
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ChapterTagFilterDialog(
    chapterTags: ImmutableList<ChapterTag>,
    selectedTagIds: ImmutableSet<Long>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    // Drop ids whose tag no longer exists so a stale selection can't be re-submitted.
    val mutableSelected = remember(selectedTagIds, chapterTags) {
        selectedTagIds.filter { id -> chapterTags.any { it.id == id } }.toMutableStateList()
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(KMR.strings.action_filter_chapter_tags)) },
        text = textFunc@{
            if (chapterTags.isEmpty()) {
                Text(text = stringResource(KMR.strings.information_empty_chapter_tags_filter))
                return@textFunc
            }
            Box {
                val state = rememberLazyListState()
                LazyColumn(state = state) {
                    items(
                        items = chapterTags,
                        contentType = { "item" },
                        key = { it.id },
                    ) { tag ->
                        val isSelected = mutableSelected.contains(tag.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    if (isSelected) {
                                        mutableSelected.remove(tag.id)
                                    } else {
                                        mutableSelected.add(tag.id)
                                    }
                                }
                                .minimumInteractiveComponentSize()
                                .clip(MaterialTheme.shapes.small)
                                .fillMaxWidth()
                                .padding(horizontal = MaterialTheme.padding.small),
                        ) {
                            Icon(
                                imageVector = if (isSelected) {
                                    Icons.Rounded.CheckBox
                                } else {
                                    Icons.Rounded.CheckBoxOutlineBlank
                                },
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                                contentDescription = null,
                            )
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        }
                    }
                }
                if (state.canScrollBackward) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                if (state.canScrollForward) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmButton = {
            if (chapterTags.isEmpty()) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            } else {
                FlowRow {
                    TextButton(onClick = mutableSelected::clear) {
                        Text(text = stringResource(MR.strings.action_reset))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            onConfirm(mutableSelected.toSet())
                            onDismissRequest()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            }
        },
    )
}
