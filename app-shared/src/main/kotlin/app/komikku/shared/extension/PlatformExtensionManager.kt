package app.komikku.shared.extension

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.source.model.StubSource

/**
 * KMK: the extension manager surface shared screens and interactors need, implemented by the desktop
 * extension manager (`:desktopApp`, docs/kmp-desktop/PROGRESS.md 5.2).
 *
 * It mirrors the public API of Android's `eu.kanade.tachiyomi.extension.ExtensionManager` (same
 * member names and semantics), which is too Android-bound to be borrowed (package manager,
 * install receivers, `R` resources) and is left untouched. **Future unification:** when the extension
 * screens become shared (Phase 6.6), make the Android `ExtensionManager` implement this interface (a
 * one-line KMK edit) and let `GetExtensionsByType` / `GetExtensionLanguages` take it instead of the
 * concrete class.
 */
interface PlatformExtensionManager {

    val isInitialized: StateFlow<Boolean>

    val installedExtensionsFlow: StateFlow<List<Extension.Installed>>

    val availableExtensionsFlow: StateFlow<List<Extension.Available>>

    val untrustedExtensionsFlow: StateFlow<List<Extension.Untrusted>>

    /** Package name of the installed extension that provides [sourceId]. */
    fun getExtensionPackage(sourceId: Long): String?

    /** Source metadata from the available extensions' index, for stub sources. */
    fun getSourceData(id: Long): StubSource?

    /** Refreshes [availableExtensionsFlow] from the enabled extension stores. */
    suspend fun findAvailableExtensions()

    /** Downloads, verifies and installs [extension]; the flow completes when it is installed or failed. */
    fun installExtension(extension: Extension.Available): Flow<InstallStep>

    /** Installs the newest available version of [extension] (same package and signing key). */
    fun updateExtension(extension: Extension.Installed): Flow<InstallStep>

    fun cancelInstallUpdateExtension(extension: Extension)

    fun uninstallExtension(extension: Extension)

    /** Trusts [extension]'s package version and signing key, then loads it. */
    suspend fun trust(extension: Extension.Untrusted)
}
