package eu.kanade.domain.source.model

import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/*
 * KMK: desktop counterpart of Android's `installedExtension` (app's eu/kanade/domain/source/model/Source.kt, which also
 * holds the Android-only source icon), for the borrowed MigrateSourceScreenModel (P20): the installed extension that
 * provides this source, none when the extension manager is not available.
 */
val Source.installedExtension: Extension.Installed?
    get() {
        val installed = try {
            Injekt.get<ExtensionManager>().installedExtensionsFlow.value
        } catch (_: Exception) {
            return null
        }
        return installed.find { ext -> ext.sources.any { it.id == id } }
    }
