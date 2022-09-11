package eu.kanade.tachiyomi.ui.category.biometric

import android.content.Context
import android.text.format.DateFormat
import java.util.Date
import java.util.SimpleTimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
            val index = timeRange.indexOf(',')
            return if (index != -1) {
                TimeRange(
                    timeRange.substring(0, index).toDoubleOrNull()?.minutes ?: return null,
                    timeRange.substring(index + 1).toDoubleOrNull()?.minutes ?: return null,
                )
            } else {
                return null
            }
        }
    }
}
