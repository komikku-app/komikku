package mihon.data.extension.repository

import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.model.NO_SIGNING_KEY
import mihon.domain.extension.model.canBeRefreshedTo
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler

class ExtensionStoreRepositoryImpl(
    private val service: ExtensionStoreService,
    private val handler: DatabaseHandler,
) : ExtensionStoreRepository {
    override suspend fun insert(indexUrl: String): Result<Unit> {
        return service.fetch(indexUrl).mapCatching { upsert(it) }
    }

    override suspend fun insertFromPreference(indexUrl: String, name: String) {
        handler.await {
            extension_storeQueries.upsert(
                indexUrl = indexUrl,
                name = name,
                badgeLabel = name,
                // KMK -->
                signingKey = NO_SIGNING_KEY,
                // KMK <--
                contactWebsite = indexUrl,
                contactDiscord = null,
                isLegacy = false,
                extensionListUrl = null,
            )
        }
    }

    // KMK -->
    override suspend fun refreshAll(): Map<String, String> {
        val renamed = mutableMapOf<String, String>()
        // KMK <--
        try {
            // KMK -->
            val savedStores = handler.awaitList {
                extension_storeQueries.getAll(::extensionStoreMapper)
            }
                .associateBy { it.indexUrl }
                .toMutableMap()
            savedStores.values.toList().forEach { store ->
                service.fetch(store.indexUrl)
                    // KMK <--
                    .mapCatching {
                        // KMK -->
                        // A refresh must not turn a saved store into another store (e.g. a repo that now
                        // points to another store's index), or that store's extensions would be listed
                        // under this store's signing key and name.
                        if (!store.canBeRefreshedTo(it, savedStores.values)) {
                            logcat(LogPriority.WARN) {
                                "Not refreshing extension store '${store.name} (${store.indexUrl})': it now serves " +
                                    "'${it.name} (${it.indexUrl})' signed by ${it.signingKey} " +
                                    "instead of ${store.signingKey}"
                            }
                            return@mapCatching
                        }
                        // KMK <--
                        // KMK -->
                        val applied = handler.await(inTransaction = true) {
                            // The user may have removed the store while it was being fetched:
                            // don't bring it back
                            if (extension_storeQueries.get(store.indexUrl).executeAsOneOrNull() == null) {
                                return@await false
                            }
                            // KMK <--
                            upsert(it)
                            // KMK -->
                            if (store.indexUrl != it.indexUrl) {
                                extension_storeQueries.delete(store.indexUrl)
                                renamed[store.indexUrl] = it.indexUrl
                            }
                            true
                            // KMK <--
                        }
                        // KMK -->
                        savedStores.remove(store.indexUrl)
                        if (applied) savedStores[it.indexUrl] = it
                        // KMK <--
                    }
                    .onFailure {
                        logcat(LogPriority.ERROR, it) {
                            // KMK -->
                            "Failed to refresh extension store '${store.name} (${store.indexUrl})'"
                            // KMK <--
                        }
                    }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
        // KMK -->
        return renamed
        // KMK <--
    }

    private suspend fun upsert(store: ExtensionStore) {
        handler.await {
            extension_storeQueries.upsert(
                indexUrl = store.indexUrl,
                name = store.name,
                badgeLabel = store.badgeLabel,
                signingKey = store.signingKey,
                contactWebsite = store.contact.website,
                contactDiscord = store.contact.discord,
                isLegacy = store.isLegacy,
                extensionListUrl = store.extensionListUrl,
            )
        }
    }

    override suspend fun fetchExtensions(
        // KMK -->
        disabledRepos: Set<String>,
        // KMK <--
    ): List<Extension.Available> {
        return try {
            supervisorScope {
                handler.awaitList {
                    extension_storeQueries.getAll(::extensionStoreMapper)
                }
                    // KMK -->
                    .filterNot { it.indexUrl in disabledRepos }
                    // KMK <--
                    .map { store ->
                        async {
                            service.getExtensions(store).onFailure {
                                this@ExtensionStoreRepositoryImpl.logcat(LogPriority.ERROR, it) {
                                    "Failed to fetch extensions for store '${store.name} (${store.indexUrl})'"
                                }
                            }
                        }
                    }
                    .awaitAll()
                    .flatMap { it.getOrDefault(emptyList()) }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun getAll(): List<ExtensionStore> {
        return handler.awaitList { extension_storeQueries.getAll(::extensionStoreMapper) }
    }

    override fun getAllAsFlow(): Flow<List<ExtensionStore>> {
        return handler.subscribeToList { extension_storeQueries.getAll(::extensionStoreMapper) }
    }

    override fun getCountAsFlow(): Flow<Long> {
        return handler.subscribeToOne {
            extension_storeQueries
                .getCount()
        }
    }

    override suspend fun remove(indexUrl: String) {
        handler.await { extension_storeQueries.delete(indexUrl) }
    }

    private fun extensionStoreMapper(
        indexUrl: String,
        name: String,
        badgeLabel: String,
        signingKey: String,
        contactWebsite: String,
        contactDiscord: String?,
        isLegacy: Boolean,
        extensionListUrl: String?,
    ): ExtensionStore = ExtensionStore(
        indexUrl = indexUrl,
        name = name,
        badgeLabel = badgeLabel,
        signingKey = signingKey,
        contact = ExtensionStore.Contact(
            website = contactWebsite,
            discord = contactDiscord,
        ),
        isLegacy = isLegacy,
        extensionListUrl = extensionListUrl,
    )
}
