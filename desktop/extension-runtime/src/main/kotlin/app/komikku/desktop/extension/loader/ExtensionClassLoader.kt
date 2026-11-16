package app.komikku.desktop.extension.loader

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.Collections
import java.util.Enumeration

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
 */
class ExtensionClassLoader(
    jar: File,
    parent: ClassLoader,
    private val sharedPackages: List<String> = DEFAULT_SHARED_PACKAGES,
) : URLClassLoader(arrayOf(jar.toURI().toURL()), parent) {

    private val platform: ClassLoader = getPlatformClassLoader()

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

    override fun getResourceAsStream(name: String): InputStream? = try {
        getResource(name)?.openStream()
    } catch (_: IOException) {
        null
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
