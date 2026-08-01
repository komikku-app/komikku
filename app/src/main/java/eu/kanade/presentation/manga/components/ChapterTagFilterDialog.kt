package eu.kanade.presentation.manga.components

import androidx.compose.runtime.Composable
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.chapterTag.model.ChapterTag
import tachiyomi.domain.chapterTag.model.ChapterTagFilter
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Per-manga chapter tag filter picker. Checked tags are required, inversed tags are excluded.
 */
@Composable
fun ChapterTagFilterDialog(
    chapterTags: ImmutableList<ChapterTag>,
    filter: ChapterTagFilter,
    onDismissRequest: () -> Unit,
    onConfirm: (included: Set<Long>, excluded: Set<Long>) -> Unit,
) {
    TriStateListDialog(
        title = stringResource(KMR.strings.action_filter_chapter_tags),
        message = stringResource(
            if (chapterTags.isEmpty()) {
                KMR.strings.information_empty_chapter_tags_filter
            } else {
                KMR.strings.pref_filter_chapter_tags_details
            },
        ),
        items = chapterTags,
        // Drop ids whose tag no longer exists so a stale selection can't be re-submitted.
        initialChecked = chapterTags.filter { it.id in filter.included },
        initialInversed = chapterTags.filter { it.id in filter.excluded },
        itemLabel = { it.name },
        onDismissRequest = onDismissRequest,
        onValueChanged = { included, excluded ->
            onConfirm(
                included.mapTo(mutableSetOf()) { it.id },
                excluded.mapTo(mutableSetOf()) { it.id },
            )
            onDismissRequest()
        },
    )
}
