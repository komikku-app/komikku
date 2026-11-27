package app.komikku.desktop.extension.linkcheck

import app.komikku.desktop.extension.dex.DexJarConverter
import java.io.File
import kotlin.system.exitProcess

/**
 * Command line of the link checker (`./gradlew :desktop:extension-runtime:linkCheck --args="…"`, see
 * docs/kmp-desktop/PROGRESS.md "Link check"). Arguments: extension APKs, converted jars or directories
 * containing them; options `--output=<file>` (also write the report there) and `--strict` (exit code 1
 * when a symbol is missing). APKs are converted with dex2jar into a temporary directory first.
 */
fun main(args: Array<String>) {
    val output = args.firstOrNull { it.startsWith("--output=") }?.substringAfter('=')?.let(::File)
    val strict = "--strict" in args
    val inputs = args.filterNot { it.startsWith("--") }
        .map(::File)
        .flatMap { file ->
            if (file.isDirectory) {
                file.walkTopDown().filter { it.isFile && (it.extension == "apk" || it.extension == "jar") }.sorted().toList()
            } else {
                listOf(file)
            }
        }
    if (inputs.isEmpty()) {
        System.err.println("Usage: linkCheck <extension.apk | converted.jar | directory>… [--output=report.txt] [--strict]")
        exitProcess(2)
    }

    val checker = LinkChecker()
    val tempDir = kotlin.io.path.createTempDirectory("komikku-linkcheck").toFile()
    var missing = false
    val report = buildString {
        inputs.forEach { input ->
            val result = try {
                val jar = if (input.extension == "apk") {
                    File(tempDir, input.nameWithoutExtension + ".jar").also { jar ->
                        val failed = DexJarConverter.convert(input, jar).failedMethods
                        if (failed.isNotEmpty()) appendLine("(${failed.size} method(s) of ${input.name} could not be translated by dex2jar)")
                    }
                } else {
                    input
                }
                checker.check(jar).also { if (!it.isClean) missing = true }.format(input.name)
            } catch (e: Exception) {
                missing = true
                "== ${input.name}: failed: ${e.message}\n"
            }
            append(result)
            appendLine()
        }
    }
    tempDir.deleteRecursively()
    print(report)
    output?.let {
        it.parentFile?.mkdirs()
        it.writeText(report)
        println("Report written to ${it.absolutePath}")
    }
    if (strict && missing) exitProcess(1)
}
