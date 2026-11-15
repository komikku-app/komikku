package mihon.domain.extension.interactor

import mihon.domain.extension.model.ExtensionStoresRefresh
import mihon.domain.extension.repository.ExtensionStoreRepository

class UpdateExtensionStores(
    private val repository: ExtensionStoreRepository,
) {
    // KMK -->
    suspend operator fun invoke(): ExtensionStoresRefresh {
        return repository.refreshAll()
    }
    // KMK <--
}
