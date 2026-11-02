package eu.kanade.presentation.reader

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Persistent overlay shown on the top edge of the reader page while reading.
 * Displays the current time, battery percentage, and chapter name so the user
 * can glance at this info without exiting the reader or opening the menu.
 *
 * KMK-only feature.
 */
@Composable
fun TimeOverlay(modifier: Modifier = Modifier) {
    // Clock — ticks every second
    var clock by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalTime.now()
            delay(1000L)
        }
    }
    val timeText = clock.format(DateTimeFormatter.ofPattern("HH:mm"))
    val style = getOverlayStyle()
    val strokeStyle = getOverlayStrokeStyle(style)

    OverlayItem(Icons.Outlined.Schedule, timeText, style, strokeStyle, modifier)
}

@Composable
fun BatteryOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var batteryPct by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            batteryPct = currentBatteryPercent(context)
            delay(30_000L)
        }
    }
    val batteryText = if (batteryPct >= 0) "$batteryPct%" else ""
    if (batteryText.isEmpty()) return
    val batteryIcon = if (batteryPct >= 100) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryStd
    val style = getOverlayStyle()
    val strokeStyle = getOverlayStrokeStyle(style)

    OverlayItem(batteryIcon, batteryText, style, strokeStyle, modifier)
}

@Composable
fun ChapterOverlay(chapterName: String?, modifier: Modifier = Modifier) {
    if (chapterName.isNullOrBlank()) return
    val chapterParts = remember(chapterName) {
        val trimName = chapterName.trim()

        // Find Vol/Volume prefix anywhere in the string
        val volRegex = Regex("(?:vol\\.\\s*|vol\\s+|volume\\s+|v)(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val volMatch = volRegex.find(trimName)
        val volValue = volMatch?.groupValues?.get(1)

        // Find Ch/Chapter prefix anywhere in the string (not overlapping with Vol match)
        val chRegex = Regex("(?:ch\\.\\s*|ch\\s+|chapter\\s+|c)(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        var chValue: String? = null

        val chMatches = chRegex.findAll(trimName)
        for (match in chMatches) {
            if (volMatch == null || match.range.first >= volMatch.range.last || match.range.last <= volMatch.range.first) {
                chValue = match.groupValues[1]
                break
            }
        }

        // Fallback: search for any number not overlapping with Vol match
        if (chValue == null) {
            val numRegex = Regex("\\d+(?:\\.\\d+)?")
            val numMatches = numRegex.findAll(trimName)
            for (match in numMatches) {
                if (volMatch == null || match.range.first >= volMatch.range.last || match.range.last <= volMatch.range.first) {
                    chValue = match.value
                    break
                }
            }
        }

        if (volValue != null && chValue != null) {
            "Vol $volValue  •  Ch $chValue"
        } else if (volValue != null) {
            "Vol $volValue"
        } else if (chValue != null) {
            "Ch $chValue"
        } else {
            trimName
        }
    }
    val style = getOverlayStyle()
    val strokeStyle = getOverlayStrokeStyle(style)

    OverlayItem(null, chapterParts, style, strokeStyle, modifier)
}

@Composable
fun ProgressOverlay(currentPage: Int, totalPages: Int, modifier: Modifier = Modifier) {
    if (totalPages <= 0 || currentPage <= 0) return
    val chapterPct = (currentPage * 100 / totalPages).coerceIn(0, 100)
    val progressText = "$chapterPct%"
    val style = getOverlayStyle()
    val strokeStyle = getOverlayStrokeStyle(style)

    OverlayItem(null, progressText, style, strokeStyle, modifier)
}

@Composable
private fun getOverlayStyle(): TextStyle {
    return TextStyle(
        // KMK -->
        color = MaterialTheme.colorScheme.primary,
        // KMK <--
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun getOverlayStrokeStyle(style: TextStyle): TextStyle {
    return style.copy(
        color = Color(45, 45, 45),
        drawStyle = Stroke(width = 4f),
    )
}

@Composable
private fun OverlayItem(
    icon: ImageVector?,
    text: String,
    style: TextStyle,
    strokeStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty()) return
    // Icon uses primary color; text has stroke outline for readability over any page background
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = style.color,
                modifier = Modifier.size(16.dp),
            )
        }
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = strokeStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = text,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun currentBatteryPercent(context: android.content.Context): Int {
    return try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        } else {
            -1
        }
    } catch (e: Exception) {
        -1
    }
}

private fun extractNumber(text: String, isChapter: Boolean): String {
    val trimmed = text.trim()
    val prefixPattern = if (isChapter) {
        Regex("(?:ch\\.|ch|chapter|c)\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    } else {
        Regex("(?:vol\\.|vol|volume|v)\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    }

    val prefixMatch = prefixPattern.find(trimmed)
    if (prefixMatch != null) {
        return prefixMatch.groupValues[1]
    }

    val numberPattern = Regex("\\d+(?:\\.\\d+)?")
    val numberMatch = numberPattern.find(trimmed)
    if (numberMatch != null) {
        return numberMatch.value
    }

    return trimmed
}

@PreviewLightDark
@Composable
private fun TimeOverlayPreview() {
    TachiyomiPreviewTheme {
        Surface {
            TimeOverlay()
        }
    }
}

@PreviewLightDark
@Composable
private fun BatteryOverlayPreview() {
    TachiyomiPreviewTheme {
        Surface {
            BatteryOverlay()
        }
    }
}

@PreviewLightDark
@Composable
private fun ChapterOverlayPreview() {
    TachiyomiPreviewTheme {
        Surface {
            ChapterOverlay(chapterName = "Vol. 1 Ch. 12")
        }
    }
}
