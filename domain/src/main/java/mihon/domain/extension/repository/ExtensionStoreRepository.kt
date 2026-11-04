package mihon.domain.extension.repository

import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.model.ExtensionStore

interface ExtensionStoreRepository {
    suspend fun insert(indexUrl: String): Result<Unit>

    suspend fun insertFromPreference(indexUrl: String, name: String)

    // KMK -->
    /**
     * Refreshes the metadata of every saved store.
     *
     * @return the index URLs that changed, old URL to new URL.
     */
    suspend fun refreshAll(): Map<String, String>
    // KMK <--

    suspend fun fetchExtensions(
        // KMK -->
        disabledRepos: Set<String>,
        // KMK <--
    ): List<Extension.Available>

    suspend fun getAll(): List<ExtensionStore>

    fun getAllAsFlow(): Flow<List<ExtensionStore>>

    fun getCountAsFlow(): Flow<Long>

    suspend fun remove(indexUrl: String)
}
