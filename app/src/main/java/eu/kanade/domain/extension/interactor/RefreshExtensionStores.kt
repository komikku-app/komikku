package eu.kanade.domain.extension.interactor

import eu.kanade.domain.source.service.SourcePreferences
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.model.ChangedExtensionStore
import mihon.domain.extension.model.remapIndexUrls
import tachiyomi.core.common.preference.getAndSet

/**
 * Refreshes the saved extension stores.
 *
 * A refresh can move a store to a new index URL (e.g. a legacy `repo.json` that now points to an
 * `index_v2`). Disabled stores are saved by URL, so they are moved along to stay disabled.
 */
class RefreshExtensionStores(
    private val updateExtensionStores: UpdateExtensionStores,
    private val sourcePreferences: SourcePreferences,
) {
    /**
     * @return the enabled stores that weren't refreshed because their URL now serves another store.
     * Disabled stores are left out: nothing is fetched from them, so the user doesn't need to act.
     */
    suspend operator fun invoke(): List<ChangedExtensionStore> {
        val refresh = updateExtensionStores()
        if (refresh.renamed.isNotEmpty()) {
            sourcePreferences.disabledRepos().getAndSet { it.remapIndexUrls(refresh.renamed) }
        }
        // A store that isn't refreshed keeps its URL, so the disabled list doesn't need remapping for it
        val disabledRepos = sourcePreferences.disabledRepos().get()
        return refresh.changed.filterNot { it.store.indexUrl in disabledRepos }
    }
}
