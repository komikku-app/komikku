package android.graphics.drawable

/**
 * android-compat: `android.graphics.drawable.Drawable`, as a type only.
 *
 * Shared models (`Extension.Installed.icon`) carry a `Drawable?`. Desktop code that has an icon
 * (e.g. the extension runtime reading it from an APK, Phase 5) subclasses this and keeps the raw
 * image bytes; [intrinsicWidth] / [intrinsicHeight] are `-1` when unknown, like Android.
 * There is no drawing on desktop: Compose renders the bytes directly.
 */
abstract class Drawable {

    open val intrinsicWidth: Int get() = -1

    open val intrinsicHeight: Int get() = -1
}
