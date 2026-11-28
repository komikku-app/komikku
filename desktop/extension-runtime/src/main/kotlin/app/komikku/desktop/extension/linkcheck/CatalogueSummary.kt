package app.komikku.desktop.extension.linkcheck

/**
 * Markdown summary of one or more link check reports (`LinkCheckMain`'s text output; batches of a catalogue are
 * summarised together): how many extensions convert, resolve every reference, verify and create their sources, which
 * missing `android.*` classes and members they reference anywhere (also inside method bodies) and how many extensions
 * each one concerns, and how many extensions use each compat class.
 */
object CatalogueSummary {

    private val HEADER = Regex("""^== (.+?): (?:(\d+) classes checked|failed: (.*))$""")
    private val SECTION = Regex("""^Missing (classes|methods|fields) \(\d+\):$""")
    private val ENTRY = Regex("""^ {2}(\S+) {2}<- """)
    private val UNTRANSLATED = Regex("""^\((\d+) method\(s\) of (.+?) could not be translated by dex2jar\)$""")
    private val VERIFICATION = Regex("""^Verification: (\d+) verified, (\d+) rejected, (\d+) unresolved$""")
    private const val SOURCES = "Sources: "
    private const val COMPAT = "Compat classes used: "

    private val ANDROID_PREFIXES = listOf("android.", "androidx.", "dalvik.", "com.google.android.")

    private class Extension(val name: String, val error: String?) {
        var untranslated = 0
        val missingClasses = sortedSetOf<String>()
        val missingMembers = sortedSetOf<String>()
        var verified: Boolean? = null
        var sources: String? = null
        val compat = sortedSetOf<String>()

        val sourcesCreated: Boolean get() = sources?.matches(CREATED) == true
    }

    private val CREATED = Regex("""[1-9]\d* created""")

    fun isAndroid(name: String): Boolean = ANDROID_PREFIXES.any(name::startsWith)

    fun summarize(lines: Sequence<String>): String {
        val extensions = parse(lines)
        val converted = extensions.filter { it.error == null }
        val sourcesChecked = converted.filter { it.sources != null }
        val created = sourcesChecked.filter { it.sourcesCreated }
        val androidClassUsers = converted.filter { ext -> ext.missingClasses.any(::isAndroid) }
        // A member is only reported missing when its class exists (a missing class is reported instead)
        val androidMemberUsers = converted.filter { ext -> ext.missingMembers.any(::isAndroid) }

        return buildString {
            appendLine("| | Extensions |")
            appendLine("|---|---:|")
            appendLine("| Checked | ${extensions.size} |")
            appendLine("| Converted (dex2jar) | ${converted.size} |")
            appendLine("| … with every method translated | ${converted.count { it.untranslated == 0 }} |")
            appendLine("| Every reference resolves (link check, method bodies included) | ${converted.count { it.missingClasses.isEmpty() && it.missingMembers.isEmpty() }} |")
            if (converted.any { it.verified != null }) {
                appendLine("| Every class verifies | ${converted.count { it.verified == true }} |")
            }
            if (sourcesChecked.isNotEmpty()) {
                appendLine("| Sources created | ${created.size} of ${sourcesChecked.size} |")
                appendLine("| Sources not created: unsupported on desktop | ${sourcesChecked.count { it.sources!!.startsWith("unsupported") }} |")
            }
            appendLine("| Reference a missing `android.*` class anywhere | ${androidClassUsers.size} |")
            appendLine("| Reference a missing member of an existing `android.*` class | ${androidMemberUsers.size} |")
            appendLine()

            table(
                "Missing `android.*` classes",
                converted.flatMap { ext -> ext.missingClasses.filter(::isAndroid).map { it to ext } },
                sourcesChecked.isNotEmpty(),
            )
            table(
                "Missing members of existing `android.*` classes",
                converted.flatMap { ext -> ext.missingMembers.filter(::isAndroid).map { it to ext } },
                sourcesChecked.isNotEmpty(),
            )
            table(
                "Other missing classes",
                converted.flatMap { ext -> ext.missingClasses.filterNot(::isAndroid).map { it to ext } },
                sourcesChecked.isNotEmpty(),
            )

            val failedSources = sourcesChecked.filter { it !in created }
            if (failedSources.isNotEmpty()) {
                appendLine("### Sources not created (${failedSources.size})")
                appendLine()
                appendLine("| Extension | Result |")
                appendLine("|---|---|")
                failedSources.forEach { appendLine("| ${it.name} | ${it.sources!!.replace("|", "\\|")} |") }
                appendLine()
            }
            val failed = extensions.filter { it.error != null }
            if (failed.isNotEmpty()) {
                appendLine("### Not converted (${failed.size})")
                appendLine()
                failed.forEach { appendLine("- ${it.name}: ${it.error}") }
                appendLine()
            }
            val compat = converted.flatMap { ext -> ext.compat.map { it to ext } }.groupBy({ it.first }, { it.second })
            if (compat.isNotEmpty()) {
                appendLine("### Compat classes used")
                appendLine()
                appendLine("| Class | Extensions |")
                appendLine("|---|---:|")
                compat.entries.sortedWith(compareByDescending<Map.Entry<String, List<Extension>>> { it.value.size }.thenBy { it.key })
                    .forEach { (name, users) -> appendLine("| `$name` | ${users.size} |") }
                appendLine()
            }
        }
    }

    private fun StringBuilder.table(title: String, uses: List<Pair<String, Extension>>, withSources: Boolean) {
        val byRef = uses.groupBy({ it.first }, { it.second })
        appendLine("### $title (${byRef.size})")
        appendLine()
        if (byRef.isEmpty()) {
            appendLine("None.")
            appendLine()
            return
        }
        appendLine(if (withSources) "| Reference | Extensions | … sources not created | Extensions (up to $NAMED) |" else "| Reference | Extensions | Extensions (up to $NAMED) |")
        appendLine(if (withSources) "|---|---:|---:|---|" else "|---|---:|---|")
        byRef.entries.sortedWith(compareByDescending<Map.Entry<String, List<Extension>>> { it.value.size }.thenBy { it.key })
            .forEach { (ref, users) ->
                val names = users.map { it.name }.sorted()
                val shown = names.take(NAMED).joinToString() + if (names.size > NAMED) ", …" else ""
                val notCreated = users.count { it.sources != null && !it.sourcesCreated }
                appendLine(if (withSources) "| `$ref` | ${users.size} | $notCreated | $shown |" else "| `$ref` | ${users.size} | $shown |")
            }
        appendLine()
    }

    private fun parse(lines: Sequence<String>): List<Extension> {
        val extensions = LinkedHashMap<String, Extension>()
        val untranslated = HashMap<String, Int>()
        var current: Extension? = null
        var kind: String? = null
        for (line in lines) {
            UNTRANSLATED.matchEntire(line)?.let {
                untranslated[it.groupValues[2]] = it.groupValues[1].toInt()
                continue
            }
            HEADER.matchEntire(line)?.let {
                val name = it.groupValues[1]
                current = Extension(name, it.groups[3]?.value).also { ext -> extensions[name] = ext }
                kind = null
                continue
            }
            val ext = current ?: continue
            SECTION.matchEntire(line)?.let {
                kind = it.groupValues[1]
                continue
            }
            VERIFICATION.matchEntire(line)?.let {
                ext.verified = it.groupValues[2] == "0" && it.groupValues[3] == "0"
                kind = null
                continue
            }
            if (line.startsWith(SOURCES)) {
                ext.sources = line.removePrefix(SOURCES)
                kind = null
                continue
            }
            if (line.startsWith(COMPAT)) {
                line.removePrefix(COMPAT).split(", ").filter { it.isNotEmpty() && it != "none" }.forEach { ext.compat += it }
                kind = null
                continue
            }
            val entry = ENTRY.find(line)?.groupValues?.get(1) ?: continue
            when (kind) {
                "classes" -> ext.missingClasses += entry
                "methods", "fields" -> ext.missingMembers += entry
            }
        }
        untranslated.forEach { (name, count) -> extensions[name]?.untranslated = count }
        return extensions.values.toList()
    }

    private const val NAMED = 5
}
