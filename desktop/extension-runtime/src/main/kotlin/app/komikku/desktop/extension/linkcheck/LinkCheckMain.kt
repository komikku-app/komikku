package app.komikku.desktop.extension.linkcheck

import app.komikku.desktop.extension.dex.DexJarConverter
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """Usage: linkCheck [<extension.apk | converted.jar | directory>…] [options]
  --index=<url|file>   check every APK an extension store index lists (Keiyoushi index.pb, index.min.json); each APK
                       is downloaded into --work, checked and deleted before the next one
  --from=<n>           skip the first n inputs (batches of a catalogue)
  --count=<n>          check at most n inputs
  --work=<dir>         folder for downloads, converted jars and the run's data (default: a temporary folder)
  --verify             also load and link every class (the JVM's verifier)
  --create-sources     also instantiate the sources (needs the desktop app's class path: :desktopApp:extensionLinkCheck)
  --compat-usage       also list the compat layer's classes each extension references
  --all-checks         --verify --create-sources --compat-usage
  --output=<file>      also write the report there (written as it goes)
  --summary=<file>     write a Markdown summary of the report there
  --summarize          the inputs are reports of earlier runs: only write their summary (--summary, else stdout)
  --strict             exit code 1 when a symbol is missing"""

/**
 * Command line of the link checker (`./gradlew :desktop:extension-runtime:linkCheck --args="…"` against the runtime's
 * class path, or `:desktopApp:extensionLinkCheck` against the desktop app's, which `--create-sources` needs; see
 * docs/kmp-desktop/BUILDING.md "Extension link check"). Inputs: extension APKs, converted jars, directories containing
 * them, or an extension store index (`--index`). APKs are converted with dex2jar into the work folder first. The report
 * has one block per extension (`== <name>: <n> classes checked` or `== <name>: failed: <error>`, then the missing
 * symbols and the lines of the enabled [ExtensionChecks]); [CatalogueSummary] reads it.
 */
fun main(args: Array<String>) {
    fun option(name: String): String? = args.firstOrNull { it.startsWith("--$name=") }?.substringAfter('=')
    val flags = args.filter { it.startsWith("--") && '=' !in it }.toSet()
    val unknown = flags - setOf("--verify", "--create-sources", "--compat-usage", "--all-checks", "--summarize", "--strict")
    if (unknown.isNotEmpty()) usage("Unknown option(s): ${unknown.joinToString()}")
    val output = option("output")?.let(::File)
    val summary = option("summary")?.let(::File)
    val strict = "--strict" in flags
    val all = "--all-checks" in flags
    val files = args.filterNot { it.startsWith("--") }.map(::File)

    if ("--summarize" in flags) {
        if (files.isEmpty()) usage("--summarize needs report files")
        val text = CatalogueSummary.summarize(files.asSequence().flatMap { it.readLines() })
        if (summary != null) summary.writeText(text) else print(text)
        exitProcess(0)
    }

    val index = option("index")
    val sources: List<String> = buildList {
        files.forEach { file ->
            if (file.isDirectory) {
                file.walkTopDown().filter { it.isFile && (it.extension == "apk" || it.extension == "jar") }.sorted().forEach { add(it.path) }
            } else {
                add(file.path)
            }
        }
        if (index != null) addAll(CatalogueIndex.apkUrls(index))
    }
    val from = option("from")?.toInt() ?: 0
    val inputs = sources.drop(from).let { list -> option("count")?.toInt()?.let(list::take) ?: list }
    if (inputs.isEmpty()) usage(if (sources.isEmpty()) "No inputs" else "No inputs left after --from=$from")

    val work = option("work")?.let(::File)?.apply { mkdirs() } ?: kotlin.io.path.createTempDirectory("komikku-linkcheck").toFile()
    val temporaryWork = option("work") == null
    val hostClassLoader = LinkChecker::class.java.classLoader
    val checks = ExtensionChecks(
        verify = all || "--verify" in flags,
        createSources = all || "--create-sources" in flags,
        compatUsage = all || "--compat-usage" in flags,
        hostClassLoader = hostClassLoader,
        dataDir = File(work, "data"),
    )
    val checker = LinkChecker(hostClassLoader, trackHostClass = checks::isCompatClass)
    output?.let {
        it.parentFile?.mkdirs()
        it.writeText("")
    }
    val report = StringBuilder()
    var missing = false
    inputs.forEachIndexed { position, input ->
        val isUrl = input.startsWith("http://") || input.startsWith("https://")
        val name = if (isUrl) input.substringAfterLast('/') else File(input).name
        val block = StringBuilder()
        var downloaded: File? = null
        var jar: File? = null
        try {
            val file = if (isUrl) File(work, name).also { CatalogueIndex.download(input, it) }.also { downloaded = it } else File(input)
            val apk = file.takeIf { it.extension == "apk" }
            val classes = if (apk != null) {
                File(work, file.nameWithoutExtension + ".jar").also { converted ->
                    jar = converted
                    val failed = DexJarConverter.convert(apk, converted).failedMethods
                    if (failed.isNotEmpty()) block.appendLine("(${failed.size} method(s) of $name could not be translated by dex2jar)")
                }
            } else {
                file
            }
            val links = checker.check(classes)
            if (!links.isClean) missing = true
            block.append(links.format(name))
            checks.appendTo(block, apk, classes, links)
        } catch (e: Throwable) {
            if (e is VirtualMachineError && e !is OutOfMemoryError) throw e
            missing = true
            block.appendLine("== $name: failed: ${e.toString().lineSequence().first()}")
        } finally {
            jar?.delete()
            downloaded?.delete()
        }
        block.appendLine()
        print(block)
        System.err.println("[${from + position + 1}/${from + inputs.size}] $name")
        output?.appendText(block.toString())
        report.append(block)
    }
    if (temporaryWork) work.deleteRecursively()
    output?.let { println("Report written to ${it.absolutePath}") }
    summary?.let {
        it.parentFile?.mkdirs()
        it.writeText(CatalogueSummary.summarize(report.lineSequence()))
        println("Summary written to ${it.absolutePath}")
    }
    // Sources may leave threads running (the desktop app's graph, extension executors)
    exitProcess(if (strict && missing) 1 else 0)
}

private fun usage(problem: String): Nothing {
    System.err.println(problem)
    System.err.println(USAGE)
    exitProcess(2)
}
