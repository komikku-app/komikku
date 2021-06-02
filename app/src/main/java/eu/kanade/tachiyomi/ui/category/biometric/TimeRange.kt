package eu.kanade.tachiyomi.ui.category.biometric

import android.content.Context
import android.text.format.DateFormat
import exh.util.hours
import exh.util.minutes
import java.util.Date
import java.util.SimpleTimeZone
import kotlin.time.Duration

data class TimeRange(private val startTime: Duration, private val endTime: Duration) {
    override fun toString(): String {
        val startHour = startTime.inWholeHours
        val startMinute = (startTime - startHour.hours).inWholeMinutes
        val endHour = endTime.inWholeHours
        val endMinute = (endTime - endHour.hours).inWholeMinutes
        return String.format("%02d:%02d - %02d:%02d", startHour, startMinute, endHour, endMinute)
    }

    fun getFormattedString(context: Context): String {
        val startDate = Date(startTime.inWholeMilliseconds)
        val endDate = Date(endTime.inWholeMilliseconds)
        val format = DateFormat.getTimeFormat(context)
        format.timeZone = SimpleTimeZone(0, "UTC")

        return format.format(startDate) + " - " + format.format(endDate)
    }

    fun toPreferenceString(): String {
        return "${startTime.inWholeMinutes},${endTime.inWholeMinutes}"
    }

    fun conflictsWith(other: TimeRange): Boolean {
        return startTime in other.startTime..other.endTime || endTime in other.startTime..other.endTime
    }

    operator fun contains(other: Duration): Boolean {
        return other in startTime..endTime
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
