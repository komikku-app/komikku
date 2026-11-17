package eu.kanade.presentation.more.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.toDurationString
import tachiyomi.domain.readinglog.model.ActivityLevel
import tachiyomi.domain.readinglog.model.DayActivity
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// KMK -->
private const val ROWS = 7
private val GAP = 2.dp

/** The colors of each [ActivityLevel], from the lightest to the strongest. */
@Composable
private fun levelColors(): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    return listOf(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        primary.copy(alpha = 0.30f),
        primary.copy(alpha = 0.55f),
        primary.copy(alpha = 0.80f),
        primary,
    )
}

/**
 * A GitHub style calendar: a column for each week, a row for each day, from [start] to [end].
 * Tapping a day shows what was read that day below the grid.
 */
@Composable
fun ReadingHeatmap(
    activity: List<DayActivity>,
    start: LocalDate,
    end: LocalDate,
    modifier: Modifier = Modifier,
) {
    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val gridStart = remember(start, firstDayOfWeek) { start.startOfWeek(firstDayOfWeek) }
    val weeks = remember(gridStart, end) { (ChronoUnit.DAYS.between(gridStart, end) / ROWS).toInt() + 1 }
    val byDay = remember(activity) { activity.associateBy { it.day } }
    val colors = levelColors()
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(weeks.toFloat() / ROWS)
                .pointerInput(gridStart, end, weeks) {
                    detectTapGestures { offset ->
                        val cell = size.width.toFloat() / weeks
                        val column = (offset.x / cell).toInt()
                        val row = (offset.y / cell).toInt()
                        val day = gridStart.plusDays((column * ROWS + row).toLong())
                        selected = day.takeIf { row in 0 until ROWS && column in 0 until weeks && !it.isAfter(end) && !it.isBefore(start) }
                    }
                },
        ) {
            val cell = size.width / weeks
            val gap = GAP.toPx()
            val side = (cell - gap).coerceAtLeast(1f)
            for (column in 0 until weeks) {
                for (row in 0 until ROWS) {
                    val day = gridStart.plusDays((column * ROWS + row).toLong())
                    if (day.isBefore(start) || day.isAfter(end)) continue
                    val level = byDay[day]?.level ?: ActivityLevel.NONE
                    drawRoundRect(
                        color = colors[level.ordinal],
                        topLeft = Offset(column * cell, row * cell),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(side / 4),
                    )
                }
            }
        }

        val day = selected
        Text(
            text = if (day == null) "" else daySummary(day, byDay[day]),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.height(20.dp),
        )

        Legend(colors)
    }
}

@Composable
private fun Legend(colors: List<Color>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(KMR.strings.heatmap_less), style = MaterialTheme.typography.labelSmall)
        colors.forEach {
            Box(
                Modifier
                    .size(10.dp)
                    .background(it, RoundedCornerShape(2.dp)),
            )
        }
        Text(stringResource(KMR.strings.heatmap_more), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun daySummary(day: LocalDate, activity: DayActivity?): String {
    val date = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    if (activity == null || activity.level == ActivityLevel.NONE) {
        return stringResource(KMR.strings.heatmap_day_empty, date)
    }
    val context = LocalContext.current
    val time = activity.readTimeMs.toDuration(DurationUnit.MILLISECONDS)
        .toDurationString(context, fallback = "-")
    return stringResource(KMR.strings.heatmap_day_summary, date, time, activity.chapters)
}

private fun LocalDate.startOfWeek(firstDayOfWeek: DayOfWeek): LocalDate {
    var day = this
    while (day.dayOfWeek != firstDayOfWeek) day = day.minusDays(1)
    return day
}
// KMK <--
