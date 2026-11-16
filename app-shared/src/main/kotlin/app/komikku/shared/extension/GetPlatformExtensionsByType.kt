package app.komikku.shared.extension

import eu.kanade.domain.extension.model.Extensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * KMK: Android's `GetExtensionsByType` over a [PlatformExtensionManager] (same grouping, sorting and
 * per-source split of available extensions). Duplicated because `GetExtensionsByType` takes the
 * Android `ExtensionManager` class; see the unification note on [PlatformExtensionManager].
 */
class GetPlatformExtensionsByType(
    private val preferences: SourcePreferences,
    private val extensionManager: PlatformExtensionManager,
) {

    fun subscribe(): Flow<Extensions> {
        val showNsfwSources = preferences.showNsfwSource().get()

        return combine(
            preferences.enabledLanguages().changes(),
            extensionManager.installedExtensionsFlow,
            extensionManager.untrustedExtensionsFlow,
            extensionManager.availableExtensionsFlow,
        ) { enabledLanguages, allInstalled, allUntrusted, allAvailable ->
            val (updates, installed) = allInstalled
                .filter { showNsfwSources || !it.isNsfw }
                .sortedWith(
                    compareBy<Extension.Installed> { !it.isObsolete && !it.isRedundant }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
                .partition { it.hasUpdate }

            val untrusted = allUntrusted.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            val available = allAvailable
                .filter { extension ->
                    allInstalled.none { it.signatureHash == extension.signatureHash && it.pkgName == extension.pkgName } &&
                        allUntrusted.none { it.signatureHash == extension.signatureHash && it.pkgName == extension.pkgName } &&
                        (showNsfwSources || !extension.isNsfw)
                }
                .flatMap { ext ->
                    ext.sources.filter { it.lang in enabledLanguages }
                        .map {
                            ext.copy(
                                name = it.name,
                                lang = it.lang,
                                pkgName = "${ext.pkgName}-${it.id}",
                                sources = listOf(it),
                            )
                        }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            Extensions(updates, installed, available, untrusted)
        }
    }
}
