package app.komikku.sourcejs.engine

/** Kotlin/Native quickjs-kt has no JNI state shared between engines. */
internal actual fun <T> quickJsLifecycle(block: () -> T): T = block()
