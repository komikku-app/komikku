package app.komikku.desktop.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.viewer.calculateChapterGap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Chapter transition item (Android: `ReaderTransitionView`): current and next / previous chapter
 * names, the number of missing chapters between them, and the neighbour's loading state.
 */
@Composable
fun ChapterTransitionContent(transition: ChapterTransition, textColor: Color, modifier: Modifier = Modifier) {
    val (gap, labels) = when (transition) {
        is ChapterTransition.Next -> calculateChapterGap(transition.to, transition.from) to
            (MR.strings.transition_finished to MR.strings.transition_next)
        is ChapterTransition.Prev -> calculateChapterGap(transition.from, transition.to) to
            (MR.strings.transition_current to MR.strings.transition_previous)
    }
    Box(modifier = modifier.testTag("chapter-transition"), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 480.dp).padding(24.dp),
        ) {
            val to = transition.to
            if (to == null) {
                val text = if (transition is ChapterTransition.Next) MR.strings.transition_no_next else MR.strings.transition_no_previous
                Text(stringResource(text), color = textColor, style = MaterialTheme.typography.titleMedium)
                return@Column
            }
            Text(stringResource(labels.first), color = textColor, fontWeight = FontWeight.Bold)
            Text(transition.from.chapter.name, color = textColor)
            Text(stringResource(labels.second), color = textColor, fontWeight = FontWeight.Bold)
            Text(to.chapter.name, color = textColor)
            if (gap > 0) {
                Text(
                    pluralStringResource(MR.plurals.missing_chapters_warning, count = gap, gap),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val state by to.stateFlow.collectAsState()
            when (val s = state) {
                ReaderChapter.State.Loading -> Text(stringResource(MR.strings.transition_pages_loading), color = textColor)
                is ReaderChapter.State.Error -> Text(
                    stringResource(MR.strings.transition_pages_error, s.error.message.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {}
            }
        }
    }
}
