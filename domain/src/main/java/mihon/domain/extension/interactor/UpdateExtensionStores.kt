package mihon.domain.extension.interactor

import mihon.domain.extension.repository.ExtensionStoreRepository

class UpdateExtensionStores(
    private val repository: ExtensionStoreRepository,
) {
    // KMK -->
    /** @return the index URLs that changed, old URL to new URL. */
    suspend operator fun invoke(): Map<String, String> {
        return repository.refreshAll()
    }
    // KMK <--
}
