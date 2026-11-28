package app.komikku.desktop.compat

import java.io.File

/**
 * Implemented by the class loaders of extensions (`:desktop:extension-runtime`): [extensionDataDir] is the extension's
 * own data folder, `extensions-data/<package>/`, or `null` for a loader that runs no extension code (verification).
 */
interface ExtensionDataOwner {
    val extensionDataDir: File?
}

/**
 * Which data folder a context path is for. Every extension resolves the one `Application` of the app
 * (`Injekt.get<Application>()`), so the caller decides: when the code asking a context for a path (`dataDir`,
 * `filesDir`, `cacheDir`, `getDir`, `getDatabasePath`, `applicationInfo.dataDir`, …) is an extension's, it gets that
 * extension's folder, never the app's data folder (ATLAS_INSTRUCTIONS.md ANSWER 010). Host code, also when an extension
 * calls it, keeps the app's folders: a host object created on an extension's call (the network cache) stays the app's.
 */
internal object ExtensionDataFolders {

    private val walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

    /** Packages of the compat layer and the runtimes between the caller and the context; skipped. */
    private val PASS_THROUGH = listOf("android.", "androidx.", "app.komikku.desktop.compat.", "kotlin.", "java.", "jdk.", "sun.")

    /** The data folder of the extension whose code asks, or `null` when the caller is host code. */
    fun forCaller(): File? = walker.walk { frames ->
        frames.map { it.declaringClass }
            .filter { type -> PASS_THROUGH.none(type.name::startsWith) }
            .findFirst()
            .map { (it.classLoader as? ExtensionDataOwner)?.extensionDataDir }
            .orElse(null)
    }
}
