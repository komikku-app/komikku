package app.komikku.sourcejs.engine

/**
 * Runs [block], which creates or closes a `quickjs-kt` engine, under the process-wide lifecycle lock where the
 * platform needs one (JVM and Android: quickjs-kt's JNI layer keeps unsynchronised process-wide state, see the
 * `jvmShared` actual; PROGRESS.md "Review fixes (I2b)").
 */
internal expect fun <T> quickJsLifecycle(block: () -> T): T
