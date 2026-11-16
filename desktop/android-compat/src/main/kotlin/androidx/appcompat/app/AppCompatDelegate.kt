package androidx.appcompat.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * android-compat: the static night-mode API of `androidx.appcompat.app.AppCompatDelegate`.
 * Needed by the app's `ThemeMode.kt` (`setAppCompatDelegateThemeMode`), shared with desktop.
 * The desktop UI observes [defaultNightModeFlow] to pick light / dark instead of an Android
 * configuration change.
 */
abstract class AppCompatDelegate {

    companion object {
        const val MODE_NIGHT_FOLLOW_SYSTEM = -1
        const val MODE_NIGHT_UNSPECIFIED = -100
        const val MODE_NIGHT_NO = 1
        const val MODE_NIGHT_YES = 2
        const val MODE_NIGHT_AUTO_BATTERY = 3

        private val nightMode = MutableStateFlow(MODE_NIGHT_UNSPECIFIED)

        /** Desktop addition: the current default night mode as a flow. */
        @JvmStatic
        val defaultNightModeFlow: StateFlow<Int> = nightMode.asStateFlow()

        @JvmStatic
        fun setDefaultNightMode(mode: Int) {
            nightMode.value = mode
        }

        @JvmStatic
        fun getDefaultNightMode(): Int = nightMode.value
    }
}
