package app.komikku.desktop.compat

/**
 * Thrown by a `:desktop:android-compat` member that cannot work on desktop and would mislead if it did nothing (the
 * compat layer policy, `desktop/android-compat/COMPAT_REGISTRY.md`). The extension loader turns it into the
 * extension's "unsupported on desktop: needs …" state, with [needs] as what is missing. The message always starts with
 * `android-compat:` so gaps are easy to find in logs.
 */
class UnsupportedOnDesktop(
    /** The compat class, e.g. `android.os.Looper`. */
    val className: String,
    /** The member, e.g. `loop()`. */
    val member: String,
) : UnsupportedOperationException("android-compat: $className.$member is not supported on desktop") {

    /** `<class>.<member>`, as the Extensions tab shows it. */
    val needs: String get() = "$className.$member"
}

/** Throws [UnsupportedOnDesktop] for [member] of [className]. */
fun unsupported(className: String, member: String): Nothing = throw UnsupportedOnDesktop(className, member)
