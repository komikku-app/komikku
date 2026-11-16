package eu.kanade.tachiyomi.source

import app.komikku.shared.extension.PlatformExtensionManager
import eu.kanade.domain.source.service.SourcePreferences
import exh.source.EH_PACKAGE
import exh.source.LOCAL_SOURCE_PACKAGE
import exh.source.isEhBasedSource
import tachiyomi.domain.source.model.StubSource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/*
 * KMK: desktop counterparts of the `Source` extensions in app `eu/kanade/tachiyomi/source/SourceExtensions.kt`
 * that `desktopCopy` borrowed files call (`GetIncognitoState`). Same bodies, with the extension
 * package resolved through `PlatformExtensionManager` instead of Android's `ExtensionManager`.
 */

fun Source.isLocalOrStub(): Boolean = isLocal() || this is StubSource

fun Source.isIncognitoModeEnabled(incognitoExtensions: Set<String>? = null): Boolean {
    val extensionPackage = when {
        isLocal() -> LOCAL_SOURCE_PACKAGE
        isEhBasedSource() -> EH_PACKAGE
        else -> Injekt.get<PlatformExtensionManager>().getExtensionPackage(id)
    }
    return extensionPackage in (incognitoExtensions ?: Injekt.get<SourcePreferences>().incognitoExtensions().get())
}
