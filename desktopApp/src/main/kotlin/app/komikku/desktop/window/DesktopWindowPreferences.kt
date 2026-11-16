package app.komikku.desktop.window

import tachiyomi.core.common.preference.PreferenceStore

/**
 * Main window placement, persisted in the main preference store (`preferences.json`).
 * Positions and sizes are in dp; a negative position means "let the OS place the window".
 */
class DesktopWindowPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun windowX() = preferenceStore.getInt("desktop_window_x", UNSET_POSITION)

    fun windowY() = preferenceStore.getInt("desktop_window_y", UNSET_POSITION)

    fun windowWidth() = preferenceStore.getInt("desktop_window_width", DEFAULT_WIDTH)

    fun windowHeight() = preferenceStore.getInt("desktop_window_height", DEFAULT_HEIGHT)

    fun windowMaximized() = preferenceStore.getBoolean("desktop_window_maximized", false)

    companion object {
        const val UNSET_POSITION = Int.MIN_VALUE
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 800
        const val MIN_WIDTH = 480
        const val MIN_HEIGHT = 360
    }
}
