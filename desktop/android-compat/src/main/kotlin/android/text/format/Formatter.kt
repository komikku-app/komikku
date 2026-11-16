package android.text.format

import android.content.Context
import java.util.Locale

/**
 * android-compat: `android.text.format.Formatter`. File sizes use SI units (1 kB = 1000 B) and
 * the English unit names, like Android since 8.0; no resources are involved.
 */
object Formatter {

    private val units = arrayOf("B", "kB", "MB", "GB", "TB", "PB")

    @JvmStatic
    fun formatFileSize(context: Context?, sizeBytes: Long): String = format(sizeBytes, short = false)

    @JvmStatic
    fun formatShortFileSize(context: Context?, sizeBytes: Long): String = format(sizeBytes, short = true)

    private fun format(sizeBytes: Long, short: Boolean): String {
        val negative = sizeBytes < 0
        var value = kotlin.math.abs(sizeBytes).toDouble()
        var unit = 0
        while (value > 900 && unit < units.lastIndex) {
            value /= 1000
            unit++
        }
        val text = when {
            unit == 0 || value >= 100 -> String.format(Locale.ROOT, "%.0f", value)
            value >= 10 || short -> String.format(Locale.ROOT, if (short) "%.0f" else "%.1f", value)
            else -> String.format(Locale.ROOT, "%.2f", value)
        }
        return (if (negative) "-" else "") + text + " " + units[unit]
    }
}
