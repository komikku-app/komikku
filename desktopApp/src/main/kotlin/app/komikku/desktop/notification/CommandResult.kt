package app.komikku.desktop.notification

import java.io.IOException
import java.util.concurrent.TimeUnit

/** How a notification command ended (`notify-send`, `gdbus`, `osascript`: [runNotificationCommand]). */
data class CommandResult(
    val exitCode: Int?,
    val timedOut: Boolean = false,
    val failure: String? = null,
) {
    /** The command exited with 0: the notification service took the notification. */
    val accepted: Boolean get() = exitCode == 0

    override fun toString(): String = when {
        failure != null -> "could not run ($failure)"
        timedOut -> "timed out"
        else -> "exited with $exitCode"
    }

    companion object {
        val ACCEPTED = CommandResult(0)
        val TIMED_OUT = CommandResult(null, timedOut = true)
    }
}

/** Runs [command] with its output discarded; a command still running after [timeoutSeconds] is killed. */
fun runNotificationCommand(command: List<String>, timeoutSeconds: Long): CommandResult {
    val process = try {
        ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
    } catch (e: IOException) {
        return CommandResult(null, failure = e.message ?: e.toString())
    }
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor(1, TimeUnit.SECONDS)
        return CommandResult.TIMED_OUT
    }
    return CommandResult(process.exitValue())
}
