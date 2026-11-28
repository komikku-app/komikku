/*
 * The .deb's postinst and prerm (this folder) instead of jpackage's. jpackage's run `xdg-desktop-menu install` /
 * `uninstall` under `set -e`, and xdg-desktop-menu exits with 3 unless XDG_DATA_DIRS has both an applications and a
 * desktop-directories folder it can write, which a system without a desktop lacks, so the install failed there (the
 * Linux smoke job's runner, run 46120905511), and so would the removal. Compose
 * passes jpackage its own `--resource-dir` after any extra argument (jpackage keeps the last), so the scripts are
 * replaced in the built package: unpacked and packed again with `dpkg-deb`, which jpackage itself needs to build
 * a .deb. The build fails when jpackage's scripts run anything else than the one menu command, so a later jpackage
 * or a new packaging option (file associations, services) is noticed instead of dropped.
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
                val commands = original.readLines().map { it.trim() }.filter { "xdg-" in it }
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

fun runTool(vararg command: String) {
    val process = ProcessBuilder(*command).inheritIO().start()
    check(process.waitFor() == 0) { "${command.joinToString(" ")} exited with ${process.exitValue()}" }
}
