package app.komikku.desktop.ui

import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.minutes

/**
 * KMK: desktop counterpart of Android's `relativeTimeSpanString` (`eu.kanade.presentation.util.TimeUtils`, not
 * borrowed: it needs `android.text.format.DateUtils`): "Never" for 0, "Just now" within a minute, otherwise the
 * localized date and time (Android: `DateUtils`' relative span such as "5 minutes ago").
 */
@Composable
fun relativeTimeSpanString(epochMillis: Long, now: Long = System.currentTimeMillis()): String = when {
    epochMillis <= 0L -> stringResource(MR.strings.relative_time_span_never)
    now - epochMillis < 1.minutes.inWholeMilliseconds -> stringResource(MR.strings.updates_last_update_info_just_now)
    else -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}
