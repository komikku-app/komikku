package eu.kanade.domain.source.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.isIncognitoModeEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.source.service.SourceManager

class GetIncognitoState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
    private val sourceManager: SourceManager,
    // KMK -->
    private val customMangaManager: GetCustomMangaInfo,
    // KMK <--
) {
    fun await(sourceId: Long? = null, mangaId: Long? = null): Boolean {
        if (basePreferences.incognitoMode().get()) return true
        // KMK -->
        var isIncognito = false
        if (sourceId != null) {
            val source = sourceManager.get(sourceId)
            if (source != null) isIncognito = source.isIncognitoModeEnabled() == true
        }
        if (mangaId != null) {
            val manga = customMangaManager.get(mangaId)
            if (manga != null) isIncognito = isIncognito || manga.incognitoMode == true
        }
        return isIncognito
        // KMK <--
    }

    fun subscribe(sourceId: Long?, mangaId: Long? = null): Flow<Boolean> {
        if (sourceId == null && mangaId == null) return basePreferences.incognitoMode().changes()

        return combine(
            basePreferences.incognitoMode().changes(),
            sourcePreferences.incognitoExtensions().changes(),
            if (sourceId != null) extensionManager.getExtensionPackageAsFlow(sourceId) else flow { emit(null) },
            if (sourceId != null) flow { emit(sourceManager.get(sourceId)?.isIncognitoModeEnabled() == true) } else flow { emit(false) },
            if (mangaId != null) flow { emit(customMangaManager.getIncognitoMode(mangaId)) } else flow { emit(false) },
        ) { incognito, incognitoExtensions, extensionPackage, isSourceIncognito, isMangaIncognito ->
            incognito || (extensionPackage in incognitoExtensions) || isSourceIncognito || isMangaIncognito
        }
            .distinctUntilChanged()
    }
}
