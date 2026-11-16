package eu.kanade.tachiyomi.ui.reader.setting

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

/**
 * KMK: desktop counterpart of the Android `ReadingMode` (app `ui/reader/setting/ReadingMode.kt`):
 * the same entries, flag values, labels, directions and viewer types, without the Android icon
 * resources and the `View`-based viewer factory. The values are stored in `Manga.viewerFlags` and in
 * `ReaderPreferences.defaultReadingMode()` exactly like on Android, so both apps read each other's
 * settings (e.g. after a backup restore).
 */
enum class ReadingMode(
    val stringRes: StringResource,
    val flagValue: Int,
    val direction: Direction? = null,
    val type: ViewerType? = null,
) {
    DEFAULT(MR.strings.label_default, 0x00000000),
    LEFT_TO_RIGHT(MR.strings.left_to_right_viewer, 0x00000001, Direction.Horizontal, ViewerType.Pager),
    RIGHT_TO_LEFT(MR.strings.right_to_left_viewer, 0x00000002, Direction.Horizontal, ViewerType.Pager),
    VERTICAL(MR.strings.vertical_viewer, 0x00000003, Direction.Vertical, ViewerType.Pager),
    WEBTOON(MR.strings.webtoon_viewer, 0x00000004, Direction.Vertical, ViewerType.Webtoon),
    CONTINUOUS_VERTICAL(MR.strings.vertical_plus_viewer, 0x00000005, Direction.Vertical, ViewerType.Webtoon),
    ;

    companion object {
        const val MASK = 0x00000007

        fun fromPreference(preference: Int?): ReadingMode = entries.find { it.flagValue == preference } ?: DEFAULT

        fun isPagerType(preference: Int): Boolean {
            val mode = fromPreference(preference)
            return mode.type is ViewerType.Pager
        }
    }

    sealed interface Direction {
        data object Horizontal : Direction
        data object Vertical : Direction
    }

    sealed interface ViewerType {
        data object Pager : ViewerType
        data object Webtoon : ViewerType
    }
}
