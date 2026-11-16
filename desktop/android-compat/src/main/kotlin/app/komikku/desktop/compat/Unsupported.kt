package app.komikku.desktop.compat

/**
 * Exception thrown by `:desktop:android-compat` members that have no desktop implementation.
 * The message always starts with `android-compat:` so gaps are easy to find in logs.
 */
fun unsupported(what: String): Nothing = throw UnsupportedOperationException("android-compat: $what is not supported on desktop")
