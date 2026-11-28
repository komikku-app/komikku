/*
 * The Linux packages' install and removal scripts (this folder) instead of jpackage's. jpackage's run
 * `xdg-desktop-menu install` / `uninstall`, and xdg-desktop-menu exits with 3 unless XDG_DATA_DIRS has both an
 * applications and a desktop-directories folder it can write, which a system without a desktop lacks. The .deb's run
 * under `set -e`, so its install failed there (the Linux smoke job's runner, run 46120905511), and so would its
 * removal; the RPM's post only makes rpm warn (dnf reports an error), but its preun stops the erase.
 *
 * Compose passes jpackage its own `--resource-dir` after any extra argument (jpackage keeps the last), so jpackage
 * cannot be given the scripts as templates. The .deb is unpacked and packed again with `dpkg-deb` (which jpackage
 * itself needs to build a .deb); for the RPM, jpackage keeps its working folder (`--temp`), and the spec's scriptlets
 * are replaced and `rpmbuild` run again as jpackage ran it. The build fails when jpackage's scripts run anything else
 * than the one menu command, so a later jpackage or a new packaging option (file associations, services) is noticed
 * instead of dropped.
 */

val debScripts = layout.projectDirectory.dir("packaging/linux")
val desktopFile = "/opt/komikku/lib/komikku-Komikku.desktop"
val expectedCommands = mapOf(
    "postinst" to "xdg-desktop-menu install $desktopFile",
    "prerm" to "xdg-desktop-menu uninstall $desktopFile",
)

tasks.named { it == "packageDeb" || it == "packageReleaseDeb" }.configureEach {
    val scripts = expectedCommands.keys.map { debScripts.file(it).asFile }
    inputs.files(scripts)
    doLast {
        val debs = outputs.files.asFileTree.matching { include("**/*.deb") }.files
        check(debs.isNotEmpty()) { "$name built no .deb" }
        val work = temporaryDir.resolve("deb")
        for (deb in debs) {
            work.deleteRecursively()
            runTool("dpkg-deb", "--raw-extract", deb.path, work.path)
            val control = work.resolve("DEBIAN")
            for (script in scripts) {
                val original = control.resolve(script.name)
                val commands = original.readLines().map { it.trim() }.filter(::runsXdgCommand)
                check(commands == listOf(expectedCommands.getValue(script.name))) {
                    "jpackage's ${script.name} in ${deb.name} runs $commands; update desktopApp/packaging/linux"
                }
                script.copyTo(original, overwrite = true)
                original.setExecutable(true, false)
            }
            runTool("dpkg-deb", "--root-owner-group", "--build", work.path, deb.path)
        }
        work.deleteRecursively()
    }
}

/** The RPM spec's scriptlets: what jpackage's contain (blank lines left out), and the file that replaces it (`null`: kept). */
val rpmScriptlets = listOf(
    Triple("%pre", listOf("package_type=rpm", "if [ \"\$1\" = 2 ]; then", "true;", "fi"), null),
    Triple("%post", listOf("package_type=rpm", "xdg-desktop-menu install $desktopFile"), "rpm-post.sh"),
    Triple("%preun", listOf("package_type=rpm", "xdg-desktop-menu uninstall $desktopFile"), "rpm-preun.sh"),
)

tasks.named { it == "packageRpm" || it == "packageReleaseRpm" }.configureEach {
    val scriptlets = rpmScriptlets.mapNotNull { it.third }.map { debScripts.file(it).asFile }
    inputs.files(scriptlets)
    // jpackage builds the RPM in this folder (the spec in SPECS/, the app image in images/) and keeps it
    val jpackageTemp = temporaryDir.resolve("jpackage")
    @Suppress("UNCHECKED_CAST")
    (property("freeArgs") as ListProperty<String>).addAll("--temp", jpackageTemp.path)
    // jpackage refuses a folder that is not empty
    doFirst { jpackageTemp.deleteRecursively() }
    doLast {
        val rpm = outputs.files.asFileTree.matching { include("**/*.rpm") }.files.singleOrNull()
            ?: error("$name did not build one .rpm")
        val spec = jpackageTemp.resolve("SPECS").listFiles { file -> file.extension == "spec" }?.singleOrNull()
            ?: error("$name: no spec in $jpackageTemp/SPECS")
        spec.writeText(replaceRpmScriptlets(spec.readText(), rpm.name))
        runTool(
            "rpmbuild", "-bb", spec.path,
            "--define", "%_sourcedir ${jpackageTemp.resolve("images")}",
            "--define", "%_rpmdir ${rpm.parent}",
            "--define", "%_topdir $jpackageTemp",
            "--define", "%_rpmfilename ${rpm.name}",
        )
        jpackageTemp.deleteRecursively()
    }
}

/** Whether a script line calls an `xdg-` tool; the spec also names the `xdg-utils` package in `%if` and `Requires:`. */
fun runsXdgCommand(line: String): Boolean = Regex("""(^|[;&|(]\s*)xdg-[\w-]+(\s|$)""").containsMatchIn(line)

fun replaceRpmScriptlets(spec: String, rpmName: String): String {
    val lines = spec.lines().toMutableList()
    val commands = lines.map { it.trim() }.filter(::runsXdgCommand)
    val expected = rpmScriptlets.flatMap { it.second }.filter(::runsXdgCommand)
    check(commands == expected) { "jpackage's spec for $rpmName runs $commands; update desktopApp/packaging/linux" }
    for ((section, body, replacement) in rpmScriptlets) {
        val start = lines.indexOf(section)
        check(start >= 0) { "jpackage's spec for $rpmName has no $section; update desktopApp/packaging/linux" }
        var end = start + 1
        while (end < lines.size && !lines[end].startsWith("%")) end++
        val actual = lines.subList(start + 1, end).map { it.trim() }.filter { it.isNotEmpty() }
        check(actual == body) { "jpackage's $section for $rpmName is $actual; update desktopApp/packaging/linux" }
        if (replacement != null) {
            lines.subList(start + 1, end).clear()
            lines.addAll(start + 1, debScripts.file(replacement).asFile.readLines() + "")
        }
    }
    return lines.joinToString("\n")
}

fun runTool(vararg command: String) {
    val process = ProcessBuilder(*command).inheritIO().start()
    check(process.waitFor() == 0) { "${command.joinToString(" ")} exited with ${process.exitValue()}" }
}
