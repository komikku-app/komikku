package mihon.data.extension.service

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.data.extension.model.NetworkExtensionStore
import mihon.data.extension.model.NetworkLegacyExtension
import mihon.data.extension.model.NetworkLegacyExtensionRepo
import mihon.data.extension.model.toAvailableExtensions
import mihon.domain.extension.model.ExtensionStore
import okio.BufferedSource
import okio.buffer
import okio.gzip
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.cancellation.CancellationException

class ExtensionStoreService(
    private val network: NetworkHelper,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) {
    suspend fun fetch(indexUrl: String): Result<ExtensionStore> {
        var updatedIndexUrl: String = indexUrl
        return try {
            // KMK -->
            val store = network.client.newCall(GET(updatedIndexUrl)).awaitSuccess().use { response ->
                val source = response.body.source().decompressIfGzipped()
                // KMK <--
                val networkStore = when (source.peek().readByte()) {
                    // "[..."
                    0x5B.toByte() -> run {
                        if (!indexUrl.endsWith("/index.min.json")) {
                            throw IllegalArgumentException("Provided legacy store url is not valid")
                        }
                        updatedIndexUrl = indexUrl.replace("/index.min.json", "/repo.json")
                        network.client.newCall(GET(updatedIndexUrl)).awaitSuccess().use {
                            json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(it.body.source())
                        }
                    }
                    // "{..."
                    0x7B.toByte() -> try {
                        json.decodeFromBufferedSource<NetworkLegacyExtensionRepo>(source.peek())
                        // KMK -->
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // KMK <--
                        json.decodeFromBufferedSource<NetworkExtensionStore>(source)
                    }
                    else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                }

                if (networkStore is NetworkLegacyExtensionRepo && networkStore.indexV2 != null) {
                    return fetch(networkStore.indexV2)
                }

                networkStore.toExtensionStore(updatedIndexUrl)
            }
            Result.success(store)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Failed to add extension store '$updatedIndexUrl'"
            }
            Result.failure(e)
        }
    }

    suspend fun getExtensions(store: ExtensionStore): Result<List<Extension.Available>> {
        return try {
            val extensions = if (store.extensionListUrl != null) {
                // KMK -->
                network.client.newCall(GET(store.extensionListUrl!!)).awaitSuccess().use { response ->
                    val source = response.body.source().decompressIfGzipped()
                    // KMK <--
                    when (source.peek().readByte()) {
                        // "{..."
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkExtensionStore.ExtensionList>(source)
                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(
                            source.readByteArray(),
                        )
                    }
                        .toAvailableExtensions(store)
                }
            } else if (!store.isLegacy) {
                // KMK -->
                network.client.newCall(GET(store.indexUrl)).awaitSuccess().use { response ->
                    val source = response.body.source().decompressIfGzipped()
                    // KMK <--
                    when (source.peek().readByte()) {
                        // "{..."
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkExtensionStore>(source)
                        else -> protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                    }
                        // KMK -->
                        // The extensions are listed under the saved store's signing key, so only accept
                        // them if the index is still signed by that key. Otherwise the URL serves another
                        // store now, and a refresh of the store decides what to do with it.
                        .also { fetched ->
                            check(fetched.signingKey == store.signingKey) {
                                "Extension store '${store.name} (${store.indexUrl})' now serves '${fetched.name}' " +
                                    "signed by ${fetched.signingKey} instead of ${store.signingKey}"
                            }
                        }
                        // KMK <--
                        .extensionList!!
                        .toAvailableExtensions(store)
                }
            } else {
                val storeBaseUrl = store.indexUrl.removeSuffix("/repo.json")
                // KMK -->
                network.client.newCall(GET("$storeBaseUrl/index.min.json")).awaitSuccess().use { response ->
                    val source = response.body.source()
                    // KMK <--
                    json.decodeFromBufferedSource<List<NetworkLegacyExtension>>(source)
                        .map { it.toAvailableExtension(store, storeBaseUrl) }
                }
            }
            Result.success(extensions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun BufferedSource.decompressIfGzipped(): BufferedSource {
        val isGzip = peek().use { peeked ->
            try {
                peeked.readShort().toInt() == 0x1f8b
            } catch (_: Exception) {
                false
            }
        }

        return if (isGzip) gzip().buffer() else this
    }
}
