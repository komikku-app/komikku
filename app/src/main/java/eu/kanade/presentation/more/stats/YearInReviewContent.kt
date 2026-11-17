package eu.kanade.presentation.more.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.presentation.more.stats.components.ReadingHeatmap
import eu.kanade.presentation.more.stats.components.StatsItem
import eu.kanade.presentation.util.toDurationString
import tachiyomi.domain.readinglog.model.YearInReview
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// KMK -->
@Composable
fun YearInReviewContent(
    year: Int,
    review: YearInReview?,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    hideTitles: Boolean,
    onHideTitlesChange: (Boolean) -> Unit,
    paddingValues: PaddingValues,
) {
    LazyColumn(
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        item {
            YearSelector(year, canGoBack, canGoForward, onPreviousYear, onNextYear)
        }
        if (review == null || !review.hasData) {
            item {
                Text(
                    text = stringResource(KMR.strings.year_in_review_empty, year),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.padding.large),
                )
            }
            return@LazyColumn
        }

        item { Numbers(review) }
        item { Highlights(review, hideTitles) }
        item {
            SectionCard(KMR.strings.year_in_review_calendar) {
                ReadingHeatmap(
                    activity = review.activity,
                    start = LocalDate.of(review.year, 1, 1),
                    end = LocalDate.of(review.year, 12, 31),
                )
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            ) {
                Checkbox(checked = hideTitles, onCheckedChange = onHideTitlesChange)
                Text(
                    text = stringResource(KMR.strings.year_in_review_hide_titles),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun YearSelector(
    year: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null)
        }
        Text(text = year.toString(), style = MaterialTheme.typography.headlineMedium)
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun LazyItemScope.Numbers(review: YearInReview) {
    val context = LocalContext.current
    val time = remember(review.totalReadTimeMs) {
        review.totalReadTimeMs.toDuration(DurationUnit.MILLISECONDS).toDurationString(context, fallback = "-")
    }
    SectionCard(KMR.strings.year_in_review_title) {
        Row {
            StatsItem(time, stringResource(KMR.strings.year_in_review_time_read))
            StatsItem(review.chaptersRead.toString(), stringResource(KMR.strings.year_in_review_chapters))
        }
        Row(modifier = Modifier.padding(top = MaterialTheme.padding.medium)) {
            StatsItem(review.daysRead.toString(), stringResource(KMR.strings.year_in_review_days))
            StatsItem(review.longestStreak.toString(), stringResource(KMR.strings.year_in_review_streak))
        }
    }
}

@Composable
private fun LazyItemScope.Highlights(review: YearInReview, hideTitles: Boolean) {
    SectionCard(KMR.strings.year_in_review_highlights) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            review.busiestMonth?.let { month ->
                Text(
                    text = stringResource(
                        KMR.strings.year_in_review_busiest_month,
                        Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        review.daysReadPerMonth[month - 1],
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (review.topTitles.isNotEmpty()) {
                Text(
                    text = stringResource(KMR.strings.year_in_review_top_titles),
                    style = MaterialTheme.typography.titleSmall,
                )
                review.topTitles.forEachIndexed { index, title ->
                    Text(
                        text = if (hideTitles) "${index + 1}. ••••" else "${index + 1}. ${title.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (review.topGenres.isNotEmpty() && !hideTitles) {
                Text(
                    text = stringResource(
                        KMR.strings.year_in_review_top_genres,
                        review.topGenres.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
// KMK <--
