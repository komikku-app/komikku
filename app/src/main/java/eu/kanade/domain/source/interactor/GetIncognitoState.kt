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
        return when {
            sourceId != null -> {
                val source = sourceManager.get(sourceId)
                source?.isIncognitoModeEnabled() == true
            }
            mangaId != null -> {
                val manga = customMangaManager.get(mangaId)
                manga?.incognitoMode == true
            }
            else -> false
        }
        // KMK <--
    }

    fun subscribe(sourceId: Long?, mangaId: Long? = null): Flow<Boolean> {
        if (sourceId == null && mangaId == null) return basePreferences.incognitoMode().changes()

        return combine(
            basePreferences.incognitoMode().changes(),
            sourcePreferences.incognitoExtensions().changes(),
        ) { incognito, incognitoExtensions ->
            // KMK -->
            incognito ||
                sourceId != null &&
                sourceManager.get(sourceId)?.isIncognitoModeEnabled(incognitoExtensions) == true ||
                mangaId != null &&
                customMangaManager.getIncognitoMode(mangaId)
            // KMK <--
        }
            .distinctUntilChanged()
    }
}
