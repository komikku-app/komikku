package eu.kanade.tachiyomi.ui.category.biometric

import android.content.Context
import android.text.format.DateFormat
import com.elvishew.xlog.XLog
import java.util.Calendar
import java.util.Date
import java.util.SimpleTimeZone
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.hours
import kotlin.time.milliseconds
import kotlin.time.minutes

@ExperimentalTime
data class TimeRange(val startTime: Duration, val endTime: Duration) {
    override fun toString(): String {
        val startHour = floor(startTime.inHours).roundToInt()
        val startMinute = (startTime - floor(startTime.inHours).hours).inMinutes.roundToInt()
        val endHour = floor(endTime.inHours).roundToInt()
        val endMinute = (endTime - floor(endTime.inHours).hours).inMinutes.roundToInt()
        return String.format("%02d:%02d - %02d:%02d", startHour, startMinute, endHour, endMinute)
    }

    fun getFormattedString(context: Context): String {
        val startDate = Date(startTime.toLongMilliseconds())
        val endDate = Date(endTime.toLongMilliseconds())
        val format = DateFormat.getTimeFormat(context)
        format.timeZone = SimpleTimeZone(0, "UTC")

        return format.format(startDate) + " - " + format.format(endDate)
    }

    fun toPreferenceString(): String {
        return "${startTime.inMinutes},${endTime.inMinutes}"
    }

    fun conflictsWith(other: TimeRange): Boolean {
        return startTime in other.startTime..other.endTime || endTime in other.startTime..other.endTime
    }

    companion object {
        fun fromPreferenceString(timeRange: String): TimeRange? {
            return timeRange.split(",").mapNotNull { it.toDoubleOrNull() }.let {
                if (it.size != 2) null else {
                    TimeRange(it[0].minutes, it[1].minutes)
                }
            }
        }
    }
}
