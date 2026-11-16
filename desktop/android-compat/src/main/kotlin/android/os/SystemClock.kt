package android.os

/**
 * android-compat: `android.os.SystemClock`. Elapsed / uptime clocks are the JVM monotonic clock
 * (origin: JVM start instead of device boot).
 */
object SystemClock {

    private val origin = System.nanoTime()

    @JvmStatic
    fun elapsedRealtimeNanos(): Long = System.nanoTime() - origin

    @JvmStatic
    fun elapsedRealtime(): Long = elapsedRealtimeNanos() / 1_000_000

    @JvmStatic
    fun uptimeMillis(): Long = elapsedRealtime()

    @JvmStatic
    fun currentThreadTimeMillis(): Long =
        java.lang.management.ManagementFactory.getThreadMXBean().currentThreadCpuTime / 1_000_000

    /** Sleeps for [ms] milliseconds, ignoring interrupts (the interrupt flag is restored afterwards). */
    @JvmStatic
    fun sleep(ms: Long) {
        val end = elapsedRealtime() + ms
        var interrupted = false
        var remaining = ms
        while (remaining > 0) {
            try {
                Thread.sleep(remaining)
            } catch (_: InterruptedException) {
                interrupted = true
            }
            remaining = end - elapsedRealtime()
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
}
