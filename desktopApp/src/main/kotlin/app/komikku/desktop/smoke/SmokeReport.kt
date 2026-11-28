package app.komikku.desktop.smoke

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The UI smoke mode's report in its report folder: `report.txt` (one line per step, the network attempts, last line
 * `UI-SMOKE PASSED` / `UI-SMOKE FAILED` / `UI-SMOKE REFUSED`, the only line a workflow needs to read) and `report.json`
 * with the same data. Written again after every step, so a run that dies leaves what it got to.
 */
internal class SmokeReport(val folder: File) {

    enum class Status { PASS, FAIL }

    data class Step(
        val name: String,
        val condition: String,
        val status: Status,
        val millis: Long,
        val screenshot: String?,
        val detail: String? = null,
    )

    enum class Outcome(val line: String) {
        RUNNING("UI-SMOKE RUNNING"),
        PASSED("UI-SMOKE PASSED"),
        FAILED("UI-SMOKE FAILED"),
        REFUSED("UI-SMOKE REFUSED"),
    }

    private val environment = linkedMapOf<String, String>()
    private val steps = mutableListOf<Step>()
    private val notes = mutableListOf<String>()
    private var attempts: List<NetworkTrap.Attempt> = emptyList()
    private var failure: String? = null

    @Volatile
    var outcome: Outcome = Outcome.RUNNING
        private set

    @Synchronized
    fun environment(key: String, value: Any?) {
        environment[key] = value.toString()
    }

    @Synchronized
    fun step(step: Step) {
        steps += step
        write()
    }

    @Synchronized
    fun note(text: String) {
        notes += text
        write()
    }

    @Synchronized
    fun steps(): List<Step> = steps.toList()

    /** Ends the report: [failure] (`null` when the run passed) and the network attempts. */
    @Synchronized
    fun finish(outcome: Outcome, failure: String?, attempts: List<NetworkTrap.Attempt>) {
        this.outcome = outcome
        this.failure = failure
        this.attempts = attempts
        write()
    }

    @Synchronized
    fun write() {
        folder.mkdirs()
        writeAtomically(File(folder, TEXT_FILE), text())
        writeAtomically(File(folder, JSON_FILE), json())
    }

    private fun text(): String = buildString {
        appendLine("Komikku UI smoke report")
        environment.forEach { (key, value) -> appendLine("$key: $value") }
        appendLine()
        steps.forEach { step ->
            append("${step.status} ${step.name} ${step.millis} ms ${step.screenshot ?: "-"} (until: ${step.condition})")
            step.detail?.let { append(" — $it") }
            appendLine()
        }
        if (notes.isNotEmpty()) {
            appendLine()
            notes.forEach { appendLine("note: $it") }
        }
        appendLine()
        appendLine("network attempts: ${attempts.size}")
        attempts.forEach { attempt ->
            appendLine("  ${attempt.uri} (thread ${attempt.thread})")
            attempt.callSite.forEach { appendLine("    at $it") }
        }
        failure?.let {
            appendLine()
            appendLine("failure:")
            appendLine(it.trimEnd())
        }
        appendLine()
        appendLine(outcome.line)
    }

    private fun json(): String = JSON.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        buildJsonObject {
            put("outcome", outcome.name)
            put("environment", buildJsonObject { environment.forEach { (key, value) -> put(key, value) } })
            put(
                "steps",
                JsonArray(
                    steps.map { step ->
                        buildJsonObject {
                            put("name", step.name)
                            put("status", step.status.name)
                            put("condition", step.condition)
                            put("millis", step.millis)
                            put("screenshot", step.screenshot)
                            put("detail", step.detail)
                        }
                    },
                ),
            )
            put("notes", JsonArray(notes.map(::JsonPrimitive)))
            put(
                "networkAttempts",
                JsonArray(
                    attempts.map { attempt ->
                        buildJsonObject {
                            put("uri", attempt.uri)
                            put("thread", attempt.thread)
                            put("callSite", JsonArray(attempt.callSite.map(::JsonPrimitive)))
                        }
                    },
                ),
            )
            put("failure", failure)
        },
    )

    private fun writeAtomically(file: File, content: String) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(content)
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val TEXT_FILE = "report.txt"
        const val JSON_FILE = "report.json"
        const val LOG_FILE = "komikku.log"
        const val ACCESSIBILITY_FILE = "last-frame-accessibility.txt"

        private val JSON = Json { prettyPrint = true }
    }
}
