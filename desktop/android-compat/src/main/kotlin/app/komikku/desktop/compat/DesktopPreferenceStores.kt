package app.komikku.desktop.compat

import android.content.SharedPreferences
import tachiyomi.core.common.desktop.DesktopDirs
import tachiyomi.core.common.preference.DesktopPreferenceStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide registry of [DesktopPreferenceStore]s, one instance per file.
 *
 * `Context.getSharedPreferences(name, mode)` and `PreferenceManager.getDefaultSharedPreferences`
 * resolve through here, and desktop DI must use it too, so a `PreferenceStore` and a
 * `SharedPreferences` over the same name always share one store (two instances over one file
 * would not see each other's writes).
 */
object DesktopPreferenceStores {

    private val stores = ConcurrentHashMap<File, DesktopPreferenceStore>()
    private val sharedPreferences = ConcurrentHashMap<File, SharedPreferences>()

    /** Store for [file]. */
    fun get(file: File): DesktopPreferenceStore =
        stores.computeIfAbsent(file.canonicalKey()) { DesktopPreferenceStore(it) }

    /** Store named [name] in [directory] (default: the app's preferences directory). */
    fun get(name: String, directory: File = DesktopDirs.preferencesDir): DesktopPreferenceStore =
        get(directory.resolve(DesktopPreferenceStore.fileNameFor(name)))

    /** `SharedPreferences` view of the store for [file]. */
    fun sharedPreferences(file: File): SharedPreferences =
        sharedPreferences.computeIfAbsent(file.canonicalKey()) { DesktopSharedPreferences(get(it)) }

    /** `SharedPreferences` view of the store named [name] in [directory]. */
    fun sharedPreferences(name: String, directory: File = DesktopDirs.preferencesDir): SharedPreferences =
        sharedPreferences(directory.resolve(DesktopPreferenceStore.fileNameFor(name)))

    private fun File.canonicalKey(): File = absoluteFile.normalize()
}
