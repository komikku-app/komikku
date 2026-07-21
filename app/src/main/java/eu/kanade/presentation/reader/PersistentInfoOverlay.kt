package eu.kanade.presentation.reader

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Persistent overlay shown on the top edge of the reader page while reading.
 * Displays the current time, battery percentage, and page progress so the user
 * can glance at this info without exiting the reader or opening the menu.
 *
 * KMK-only feature.
 */
@Composable
fun PersistentInfoOverlay(
    currentPage: String,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Clock — ticks every second
    var clock by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalTime.now()
            delay(1000L)
        }
    }

    // Battery — polled every 30s via sticky broadcast
    var batteryPct by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            batteryPct = currentBatteryPercent(context)
            delay(30_000L)
        }
    }

    val timeText = clock.format(DateTimeFormatter.ofPattern("HH:mm"))
    val batteryText = if (batteryPct >= 0) "$batteryPct%" else ""
    val pageText = if (currentPage.isNotEmpty() && totalPages > 0) "$currentPage/$totalPages" else ""

    val style = TextStyle(
        // KMK -->
        color = MaterialTheme.colorScheme.primary,
        // KMK <--
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
    val strokeStyle = style.copy(
        color = Color(45, 45, 45),
        drawStyle = Stroke(width = 4f),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        OverlayText(timeText, style, strokeStyle)
        if (batteryText.isNotEmpty()) {
            OverlayText(batteryText, style, strokeStyle)
        }
        if (pageText.isNotEmpty()) {
            OverlayText(pageText, style, strokeStyle)
        }
    }
}

@Composable
private fun OverlayText(text: String, style: TextStyle, strokeStyle: TextStyle) {
    if (text.isEmpty()) return
    // Stroke (outline) drawn under the main text for readability over any page background
    Text(text = text, style = strokeStyle)
    Text(text = text, style = style)
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

@PreviewLightDark
@Composable
private fun PersistentInfoOverlayPreview() {
    TachiyomiPreviewTheme {
        Surface {
            PersistentInfoOverlay(currentPage = "10", totalPages = 69)
        }
    }
}
