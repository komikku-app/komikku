package eu.kanade.tachiyomi.ui.base.delegate

import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * KMK: the platform-neutral rules of Android's app lock (`SecureActivityDelegate`): whether a lock may happen now (SY's
 * lock times and days) and whether one is due after the app was away (Android's "Lock when idle"). Android's delegate
 * and the desktop app lock (`:desktopApp`, PIN / password instead of the device's authenticator) both use them.
 */
object AppLockRules {

    // SY's lock days (`SecurityPreferences.authenticatorDays`, a bit per day)
    const val LOCK_SUNDAY = 0x40
    const val LOCK_MONDAY = 0x20
    const val LOCK_TUESDAY = 0x10
    const val LOCK_WEDNESDAY = 0x8
    const val LOCK_THURSDAY = 0x4
    const val LOCK_FRIDAY = 0x2
    const val LOCK_SATURDAY = 0x1
    const val LOCK_ALL_DAYS = 0x7F

    /**
     * SY's `canLockNow`: [now] is inside one of the lock [timeRanges] (none = any time) and on one of the [lockedDays].
     * A time range is stored like SY's `TimeRange.toPreferenceString`: "<start minute>,<end minute>" of the day.
     */
    fun canLockNow(timeRanges: Set<String>, lockedDays: Int, now: Calendar): Boolean {
        val ranges = timeRanges.mapNotNull(::parseTimeRange)
        val canLockNow = if (ranges.isNotEmpty()) {
            val time = now.get(Calendar.HOUR_OF_DAY).hours + now.get(Calendar.MINUTE).minutes
            ranges.any { time in it }
        } else {
            true
        }

        val canLockToday = lockedDays == LOCK_ALL_DAYS ||
            when (now.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> (lockedDays and LOCK_SUNDAY) == LOCK_SUNDAY
                Calendar.MONDAY -> (lockedDays and LOCK_MONDAY) == LOCK_MONDAY
                Calendar.TUESDAY -> (lockedDays and LOCK_TUESDAY) == LOCK_TUESDAY
                Calendar.WEDNESDAY -> (lockedDays and LOCK_WEDNESDAY) == LOCK_WEDNESDAY
                Calendar.THURSDAY -> (lockedDays and LOCK_THURSDAY) == LOCK_THURSDAY
                Calendar.FRIDAY -> (lockedDays and LOCK_FRIDAY) == LOCK_FRIDAY
                Calendar.SATURDAY -> (lockedDays and LOCK_SATURDAY) == LOCK_SATURDAY
                else -> false
            }

        return canLockNow && canLockToday
    }

    /**
     * Android's "Lock when idle" ([lockAppAfter] minutes, `SecurityPreferences.lockAppAfter`): -1 never, 0 always,
     * otherwise once that many minutes have passed since [lastClosed] (milliseconds, like [nowMillis]).
     */
    fun isLockDue(lockAppAfter: Int, lastClosed: Long, nowMillis: Long): Boolean = when (lockAppAfter) {
        -1 -> false // Never
        0 -> true // Always
        else -> lastClosed + lockAppAfter * 60_000 <= nowMillis
    }

    /** SY's `TimeRange.fromPreferenceString` and `contains`, as a range of the day's time. */
    private fun parseTimeRange(timeRange: String): ClosedRange<Duration>? {
        val index = timeRange.indexOf(',')
        if (index == -1) return null
        val start = timeRange.substring(0, index).toDoubleOrNull()?.minutes ?: return null
        val end = timeRange.substring(index + 1).toDoubleOrNull()?.minutes ?: return null
        return start..end
    }
}
