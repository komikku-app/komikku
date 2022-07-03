package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.MergeMangaSettingsUpdate
import eu.kanade.domain.manga.repository.MangaMergeRepository

class UpdateMergedSettings(
    private val mangaMergeRepository: MangaMergeRepository,
) {

    suspend fun await(mergeUpdate: MergeMangaSettingsUpdate): Boolean {
        return mangaMergeRepository.updateSettings(mergeUpdate)
    }

    suspend fun awaitAll(values: List<MergeMangaSettingsUpdate>): Boolean {
        return mangaMergeRepository.updateAllSettings(values)
    }
}
