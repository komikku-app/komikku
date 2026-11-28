/*
 * komikku:// links (tracker logins redirect the browser there) open the app installed from the .msi / .exe: the
 * installer writes Software\Classes\komikku (`URL Protocol`, `shell\open\command` = "<install dir>\Komikku.exe"
 * "%1") under HKCU for our per-user install (HKLM if it ever becomes per-machine, as jpackage's own keys do).
 * Windows Installer then keeps the path current in an upgrade and removes the key with the app.
 *
 * jpackage takes extra WiX only as a main.wxs in its `--resource-dir`, and Compose passes its own (empty on Windows)
 * resource folder after any extra argument. So jpackage first runs as Compose runs it, keeping its working folder
 * (`--temp`) for the main.wxs it wrote; that file gets the component below, and jpackage runs again with Compose's
 * own argument file (build/compose/tmp/<task>.args.txt) and a resource folder holding it. The build fails when
 * jpackage's main.wxs has no single place for the component, or when jpackage does not report using it.
 */

val urlScheme = "komikku"

/** Fixed: a component's GUID must not change while its key path stays the same. */
val urlProtocolComponentGuid = "7DF08F26-883C-4AB7-A411-CA98DA965EAB"

val urlProtocolComponent = """
    <?ifdef JpIsSystemWide ?>
      <?define KmkUrlProtocolRoot = "HKLM" ?>
    <?else?>
      <?define KmkUrlProtocolRoot = "HKCU" ?>
    <?endif?>
    <DirectoryRef Id="INSTALLDIR">
      <Component Id="KmkUrlProtocol" Guid="$urlProtocolComponentGuid">
        <RegistryKey Root="${'$'}(var.KmkUrlProtocolRoot)" Key="Software\Classes\$urlScheme">
          <RegistryValue Type="string" Value="URL:Komikku" KeyPath="yes"/>
          <RegistryValue Name="URL Protocol" Type="string" Value=""/>
          <RegistryValue Key="shell\open\command" Type="string" Value="&quot;[INSTALLDIR]${'$'}(var.JpAppName).exe&quot; &quot;%1&quot;"/>
        </RegistryKey>
      </Component>
    </DirectoryRef>
"""

fun addUrlProtocol(mainWxs: String): String {
    val featureAnchor = "<ComponentGroupRef Id=\"FileAssociations\"/>"
    val productEnd = "  </Product>"
    for (anchor in listOf(featureAnchor, productEnd)) {
        check(mainWxs.split(anchor).size == 2) {
            "jpackage's main.wxs has ${mainWxs.split(anchor).size - 1} '$anchor'; update desktopApp/packaging/windows"
        }
    }
    check("Software\\Classes\\$urlScheme" !in mainWxs) { "jpackage's main.wxs already registers $urlScheme://" }
    return mainWxs
        .replace(featureAnchor, "$featureAnchor\n      <ComponentRef Id=\"KmkUrlProtocol\"/>")
        .replace(productEnd, "${urlProtocolComponent.trimEnd()}\n$productEnd")
}

tasks.named { it in setOf("packageMsi", "packageExe", "packageReleaseMsi", "packageReleaseExe") }.configureEach {
    val work = temporaryDir
    val jpackageTemp = work.resolve("jpackage")
    val composeArgs = layout.buildDirectory.file("compose/tmp/$name.args.txt")
    val extension = name.takeLast(3).lowercase()
    @Suppress("UNCHECKED_CAST")
    (property("freeArgs") as ListProperty<String>).addAll("--temp", jpackageTemp.path)
    // jpackage refuses a folder that is not empty
    doFirst { jpackageTemp.deleteRecursively() }
    doLast {
        val installer = outputs.files.asFileTree.matching { include("**/*.$extension") }.files.singleOrNull()
            ?: error("$name did not build one .$extension")
        val mainWxs = jpackageTemp.resolve("config/main.wxs")
        check(mainWxs.isFile) { "$name: jpackage left no $mainWxs" }
        val resources = work.resolve("resources").apply {
            deleteRecursively()
            mkdirs()
        }
        resources.resolve("main.wxs").writeText(addUrlProtocol(mainWxs.readText()))

        // One argument per line, each quoted; the folder after --resource-dir becomes ours, --temp is dropped
        val args = composeArgs.get().asFile.readLines().filter { it.isNotBlank() }
        val rewritten = mutableListOf<String>()
        var i = 0
        var replaced = false
        while (i < args.size) {
            when (args[i]) {
                "--temp" -> i++
                "--resource-dir" -> {
                    rewritten += args[i]
                    rewritten += "\"${resources.absolutePath.replace('\\', '/')}\""
                    replaced = true
                    i++
                }
                else -> rewritten += args[i]
            }
            i++
        }
        check(replaced) { "$name: Compose passed jpackage no --resource-dir; update desktopApp/packaging/windows" }
        if ("--verbose" !in rewritten) rewritten += "--verbose"
        val argsFile = work.resolve("jpackage-url-protocol.args.txt")
        argsFile.writeText(rewritten.joinToString("\n", postfix = "\n"))

        installer.delete()
        jpackageTemp.deleteRecursively()
        @Suppress("UNCHECKED_CAST")
        val javaHome = (property("javaHome") as Property<String>).get()
        // bin also holds jpackage.dll on Windows
        val jpackage = listOf("jpackage.exe", "jpackage").map { File(javaHome, "bin/$it") }
            .firstOrNull { it.isFile } ?: error("$name: no jpackage in $javaHome/bin")
        val wix = (property("wixToolsetDir") as DirectoryProperty).orNull?.asFile
        val process = ProcessBuilder(jpackage.path, "@${argsFile.path}").redirectErrorStream(true).apply {
            if (wix != null) environment()["PATH"] = wix.path + File.pathSeparator + (System.getenv("PATH") ?: "")
        }.start()
        val log = work.resolve("jpackage-url-protocol.log")
        log.writeText(process.inputStream.bufferedReader().readText())
        check(process.waitFor() == 0) { "jpackage with the komikku:// main.wxs exited with ${process.exitValue()}; see $log" }
        check("(loaded from main.wxs)" in log.readText()) { "jpackage did not use the komikku:// main.wxs; see $log" }
        check(installer.isFile) { "jpackage with the komikku:// main.wxs did not write $installer; see $log" }
    }
}
