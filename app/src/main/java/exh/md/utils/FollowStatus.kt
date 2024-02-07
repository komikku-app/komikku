package exh.md.utils

import java.util.Locale

enum class FollowStatus(val long: Long) {
    UNFOLLOWED(0L),
    READING(1L),
    COMPLETED(2L),
    ON_HOLD(3L),
    PLAN_TO_READ(4L),
    DROPPED(5L),
    RE_READING(6L),
    ;

    fun toDex(): String = this.name.lowercase(Locale.US)

    companion object {
        fun fromDex(
            value: String?,
        ): FollowStatus = entries.firstOrNull { it.name.lowercase(Locale.US) == value } ?: UNFOLLOWED
        fun fromLong(value: Long): FollowStatus = entries.firstOrNull { it.long == value } ?: UNFOLLOWED
    }
}
