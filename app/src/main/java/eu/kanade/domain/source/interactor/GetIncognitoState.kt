package eu.kanade.domain.source.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.isIncognitoModeEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import tachiyomi.domain.source.service.SourceManager

class GetIncognitoState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
    private val sourceManager: SourceManager,
) {
    fun await(sourceId: Long?): Boolean {
        if (basePreferences.incognitoMode().get()) return true
        if (sourceId == null) return false
        return sourceManager.get(sourceId)?.isIncognitoModeEnabled() == true
    }

    fun subscribe(sourceId: Long?): Flow<Boolean> {
        if (sourceId == null) return basePreferences.incognitoMode().changes()

        return combine(
            basePreferences.incognitoMode().changes(),
            sourcePreferences.incognitoExtensions().changes(),
            extensionManager.getExtensionPackageAsFlow(sourceId),
            flow { emit(sourceManager.get(sourceId)?.isIncognitoModeEnabled() == true) },
        ) { incognito, incognitoExtensions, extensionPackage, isSourceIncognito ->
            incognito || (extensionPackage in incognitoExtensions) || isSourceIncognito
        }
            .distinctUntilChanged()
    }
}
