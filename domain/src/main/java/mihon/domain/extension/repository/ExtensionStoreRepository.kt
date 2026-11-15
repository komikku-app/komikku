package mihon.domain.extension.repository

import kotlinx.coroutines.flow.Flow
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.model.ExtensionStoresRefresh
import mihon.domain.extension.model.FetchedExtensions

interface ExtensionStoreRepository {
    suspend fun insert(indexUrl: String): Result<Unit>

    suspend fun insertFromPreference(indexUrl: String, name: String)

    // KMK -->
    /** Refreshes the metadata of every saved store. */
    suspend fun refreshAll(): ExtensionStoresRefresh
    // KMK <--

    suspend fun fetchExtensions(
        // KMK -->
        disabledRepos: Set<String>,
    ): FetchedExtensions
    // KMK <--

    suspend fun getAll(): List<ExtensionStore>

    fun getAllAsFlow(): Flow<List<ExtensionStore>>

    fun getCountAsFlow(): Flow<Long>

    suspend fun remove(indexUrl: String)
}
