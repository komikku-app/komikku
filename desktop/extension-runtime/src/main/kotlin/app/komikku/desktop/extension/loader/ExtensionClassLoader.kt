package app.komikku.desktop.extension.loader

import app.komikku.desktop.compat.ExtensionDataOwner
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.CodeSource
import java.security.ProtectionDomain
import java.security.cert.Certificate
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Desktop counterpart of Android's `ChildFirstPathClassLoader`, over the jar converted from an
 * extension APK. Lookup order:
 *
 * 1. the JDK platform class loader (`java.*`, `javax.*`, …; Android's boot class path plays this role);
 * 2. for [sharedPackages] (the Android compat layer, `source-api`, OkHttp, okio, jsoup,
 *    kotlinx-serialization, coroutines, RxJava, Injekt, the Kotlin stdlib, …): the [parent] (host)
 *    first, so the extension and the host use the *same* classes — on Android these come from the
 *    framework or from the app, never from the extension;
 * 3. the extension jar (child first);
 * 4. the [parent].
 *
 * A class in a shared package that the host does not have is still loaded from the extension (for
 * example extension-private helpers under `eu.kanade.tachiyomi.util`).
 *
 * The jar is read into memory when the loader is created and never kept open: an update, an uninstall or a failed load
 * deletes it at once (Windows refuses to delete an open file), while the code of the version it replaced keeps
 * running for as long as something uses it (a download, a page fetch), loading the classes it has not used yet; that
 * version is freed with its last use, like any object. A class's bytes are dropped once it is defined.
 */
class ExtensionClassLoader(
    jar: File,
    parent: ClassLoader,
    private val sharedPackages: List<String> = DEFAULT_SHARED_PACKAGES,
    /** The extension's own data folder: every path the compat layer's contexts hand to its code lies inside. */
    override val extensionDataDir: File? = null,
) : ClassLoader(parent),
    ExtensionDataOwner,
    Closeable {

    private val platform: ClassLoader = getPlatformClassLoader()

    /** The jar's entries by name; a class's entry is removed once the class is defined, all of them by [close]. */
    private val entries = ConcurrentHashMap<String, ByteArray>()

    private val codeSource = CodeSource(jar.toURI().toURL(), null as Array<Certificate>?)
    private val protectionDomain = ProtectionDomain(codeSource, null, this, null)
    private val urlHandler = EntryHandler()

    init {
        ZipFile(jar).use { zip ->
            for (entry in zip.entries()) {
                if (!entry.isDirectory) entries[entry.name] = zip.getInputStream(entry).use { it.readBytes() }
            }
        }
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            val c = findLoadedClass(name)
                ?: tryLoad(platform, name)
                ?: (if (isShared(name)) tryLoad(parent, name) else null)
                ?: tryFind(name)
                ?: parent.loadClass(name)
            if (resolve) resolveClass(c)
            return c
        }
    }

    override fun findClass(name: String): Class<*> {
        val path = name.replace('.', '/') + ".class"
        val bytes = entries[path] ?: throw ClassNotFoundException(name)
        val packageName = name.substringBeforeLast('.', "")
        if (packageName.isNotEmpty() && getDefinedPackage(packageName) == null) {
            try {
                definePackage(packageName, null, null, null, null, null, null, null)
            } catch (_: IllegalArgumentException) {
                // defined meanwhile by another thread
            }
        }
        return defineClass(name, bytes, 0, bytes.size, protectionDomain).also { entries.remove(path) }
    }

    // URL.of(URI, handler) needs Java 20; the app targets 17
    @Suppress("DEPRECATION")
    override fun findResource(name: String): URL? =
        if (entries.containsKey(name)) URL("komikku-extension", null, -1, "/$name", urlHandler) else null

    override fun findResources(name: String): Enumeration<URL> = Collections.enumeration(listOfNotNull(findResource(name)))

    private fun isShared(name: String): Boolean = sharedPackages.any { name.startsWith(it) }

    private fun tryLoad(loader: ClassLoader?, name: String): Class<*>? = try {
        loader?.loadClass(name)
    } catch (_: ClassNotFoundException) {
        null
    }

    private fun tryFind(name: String): Class<*>? = try {
        findClass(name)
    } catch (_: ClassNotFoundException) {
        null
    }

    override fun getResource(name: String): URL? =
        platform.getResource(name) ?: findResource(name) ?: parent.getResource(name)

    override fun getResources(name: String): Enumeration<URL> {
        val urls = platform.getResources(name).toList() + findResources(name).toList() + parent.getResources(name).toList()
        return Collections.enumeration(urls)
    }

    override fun getResourceAsStream(name: String): InputStream? =
        platform.getResourceAsStream(name) ?: entries[name]?.let(::ByteArrayInputStream) ?: parent.getResourceAsStream(name)

    /** Drops the jar's entries: classes not loaded yet can no longer be (verification and checks; the app never does). */
    override fun close() = entries.clear()

    /** Opens `komikku-extension:` URLs ([findResource]) from [entries]. */
    private inner class EntryHandler : URLStreamHandler() {
        override fun openConnection(url: URL): URLConnection = object : URLConnection(url) {
            override fun connect() = Unit

            override fun getInputStream(): InputStream =
                ByteArrayInputStream(entries[url.path.removePrefix("/")] ?: throw java.io.FileNotFoundException(url.toString()))
        }
    }

    companion object {
        /**
         * Package prefixes that the host shares with extensions (parent first). Not the whole
         * `eu.kanade.tachiyomi.` namespace: extensions bundle their own code under
         * `eu.kanade.tachiyomi.extension.`, `.multisrc.` and `.lib.`.
         */
        val DEFAULT_SHARED_PACKAGES = listOf(
            "kotlin.",
            "kotlinx.",
            "android.",
            "androidx.",
            "dalvik.",
            "org.json.",
            "org.xmlpull.",
            "eu.kanade.tachiyomi.source.",
            "eu.kanade.tachiyomi.network.",
            "eu.kanade.tachiyomi.util.",
            "eu.kanade.tachiyomi.AppInfo",
            "tachiyomi.",
            "exh.",
            "okhttp3.",
            "okio.",
            "org.jsoup.",
            "rx.",
            "uy.kohesive.injekt.",
            "app.cash.quickjs.",
            "com.hippo.unifile.",
            "com.elvishew.xlog.",
            "logcat.",
            "nl.adaptivity.xmlutil.",
        )
    }
}
