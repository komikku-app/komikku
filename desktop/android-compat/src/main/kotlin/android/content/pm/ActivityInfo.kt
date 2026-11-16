package android.content.pm

/**
 * android-compat: `android.content.pm.ActivityInfo`, screen-orientation constants only (Android
 * values). Used by the reader orientation setting, which has no effect on desktop windows.
 */
open class ActivityInfo {
    companion object {
        const val SCREEN_ORIENTATION_UNSPECIFIED = -1
        const val SCREEN_ORIENTATION_LANDSCAPE = 0
        const val SCREEN_ORIENTATION_PORTRAIT = 1
        const val SCREEN_ORIENTATION_USER = 2
        const val SCREEN_ORIENTATION_BEHIND = 3
        const val SCREEN_ORIENTATION_SENSOR = 4
        const val SCREEN_ORIENTATION_NOSENSOR = 5
        const val SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6
        const val SCREEN_ORIENTATION_SENSOR_PORTRAIT = 7
        const val SCREEN_ORIENTATION_REVERSE_LANDSCAPE = 8
        const val SCREEN_ORIENTATION_REVERSE_PORTRAIT = 9
        const val SCREEN_ORIENTATION_FULL_SENSOR = 10
        const val SCREEN_ORIENTATION_USER_LANDSCAPE = 11
        const val SCREEN_ORIENTATION_USER_PORTRAIT = 12
        const val SCREEN_ORIENTATION_FULL_USER = 13
        const val SCREEN_ORIENTATION_LOCKED = 14
    }
}
