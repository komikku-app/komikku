package eu.kanade.domain.extension.interactor

import eu.kanade.domain.source.service.SourcePreferences
import mihon.domain.extension.interactor.UpdateExtensionStores
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
    suspend operator fun invoke() {
        val renamed = updateExtensionStores()
        if (renamed.isEmpty()) return
        sourcePreferences.disabledRepos().getAndSet { it.remapIndexUrls(renamed) }
    }
}
