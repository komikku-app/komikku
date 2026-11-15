package mihon.domain.extension.model

import eu.kanade.tachiyomi.extension.model.Extension

/**
 * A saved store whose index URL now serves another store, signed by another key (e.g. a repo that was handed
 * over, or now mirrors another store). The saved store is kept as it is, and its extensions aren't trusted
 * until the user adds it again.
 *
 * @property servedName the name of the store now served at [store]'s index URL.
 */
data class ChangedExtensionStore(
    val store: ExtensionStore,
    val servedName: String,
)

/** Thrown when a store's index is signed by another key than the saved store. */
class ExtensionStoreChangedException(val changed: ChangedExtensionStore) : IllegalStateException(
    "Extension store '${changed.store.name} (${changed.store.indexUrl})' now serves '${changed.servedName}', " +
        "signed by another key",
)

/**
 * The result of refreshing the saved stores.
 *
 * @property renamed the index URLs that changed, old URL to new URL.
 * @property changed the stores that weren't refreshed because their URL now serves another store.
 */
data class ExtensionStoresRefresh(
    val renamed: Map<String, String> = emptyMap(),
    val changed: List<ChangedExtensionStore> = emptyList(),
)

/**
 * The extensions listed by the enabled stores.
 *
 * @property changedStores the stores whose extensions were skipped because their URL now serves another store.
 */
data class FetchedExtensions(
    val extensions: List<Extension.Available> = emptyList(),
    val changedStores: List<ChangedExtensionStore> = emptyList(),
)
