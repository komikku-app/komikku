package exh.md.utils

enum class FollowStatus(val int: Int) {
    UNFOLLOWED(0),
    READING(1),
    COMPLETED(2),
    ON_HOLD(3),
    PLAN_TO_READ(4),
    DROPPED(5),
    RE_READING(6);

    companion object {
        fun fromInt(value: Int): FollowStatus? = values().find { it.int == value }
    }
}
