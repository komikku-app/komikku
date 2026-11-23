package app.komikku.shared.jssource

import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.mihon.JsSourceIds
import app.komikku.sourcejs.model.JsSourceMetadata
import app.komikku.sourcejs.repo.JsSourceOffer
import app.komikku.sourcejs.repo.JsSourceStatus
import app.komikku.sourcejs.repo.MangayomiVersions
import kotlinx.serialization.Serializable

/**
 * An installed JavaScript source as recorded in the store's `manifest.json`: the index entry it was installed from,
 * the repository that offered it, the SHA-256 of its text (the name of its code file) and when it was installed and
 * last updated (epoch milliseconds). [metadata] caches the last metadata read, so the source is usable at start-up
 * before its script ran (it is read again in the background).
 */
@Serializable
data class InstalledJsSource(
    val descriptor: JsSourceDescriptor,
    val repoUrl: String,
    val sha256: String,
    val installedAt: Long,
    val updatedAt: Long,
    val metadata: JsSourceMetadata? = null,
) {
    /** Mangayomi id (from the index). */
    val id: Long get() = descriptor.id

    /** Id of the Mihon source ([JsSourceIds.komikkuId]). */
    val sourceId: Long get() = JsSourceIds.komikkuId(descriptor.id)
}

/** The store's `manifest.json`. */
@Serializable
internal data class JsSourceManifest(
    val version: Int = VERSION,
    val sources: List<InstalledJsSource> = emptyList(),
) {
    companion object {
        const val VERSION = 1
    }
}

/**
 * An added repository (index URL). [name] / [website] come from its `repo.json`; [entries] is the number of JavaScript
 * manga entries of the last index read (`null` before the first successful read), [error] the failure of the last
 * read, if it failed.
 */
data class JsSourceRepository(
    val url: String,
    val name: String? = null,
    val website: String? = null,
    val entries: Int? = null,
    val error: JsSourceStoreException? = null,
)

/**
 * One line of the store's list: a source that is installed ([installed]) and / or offered ([offer], the best one across
 * the repositories). [status] is set for installed sources; [loadError] when its text could not be loaded.
 */
data class JsSourceItem(
    val installed: InstalledJsSource?,
    val offer: JsSourceOffer?,
    val status: JsSourceStatus?,
    val loadError: String? = null,
) {
    init {
        require(installed != null || offer != null) { "An item is installed or offered" }
    }

    val descriptor: JsSourceDescriptor get() = installed?.descriptor ?: offer!!.descriptor

    val id: Long get() = descriptor.id

    val sourceId: Long get() = JsSourceIds.komikkuId(id)

    val hasUpdate: Boolean get() = status == JsSourceStatus.UPDATE_AVAILABLE || status == JsSourceStatus.TEXT_CHANGED
}

/**
 * A downloaded, not yet installed text for [offer], shown to the user before installing: its [host] (the server that
 * served it, after redirects), [sha256] and size. [requestedHost] is the host of the offer's code URL (another one when
 * that URL redirected). [installed] is the version it would replace (an update).
 */
data class JsSourceDownload(
    val offer: JsSourceOffer,
    val host: String,
    val sha256: String,
    val sizeBytes: Long,
    val installed: InstalledJsSource?,
    val requestedHost: String = host,
) {
    val isUpdate: Boolean get() = installed != null

    /** Whether the code URL redirected to another host. */
    val redirected: Boolean get() = !requestedHost.equals(host, ignoreCase = true)

    /** An update whose text changed while the version stayed the same (the index cannot tell; the hash can). */
    val textChangedUnderSameVersion: Boolean
        get() = installed != null &&
            MangayomiVersions.compare(installed.descriptor.version, offer.descriptor.version) == 0 &&
            !installed.sha256.equals(sha256, ignoreCase = true)
}

/** A store operation that failed; [reason] lets the UI pick its message. */
class JsSourceStoreException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Reason {
        /** Not an `https` URL (also after redirects), or not a URL at all. */
        INSECURE_URL,

        /** The server did not answer with the file (network failure or HTTP error). */
        DOWNLOAD_FAILED,

        /** The file is larger than the store accepts. */
        TOO_LARGE,

        /** The repository's index is no Mangayomi index. */
        INVALID_INDEX,

        /** The downloaded text is not the one the user confirmed. */
        SHA_MISMATCH,

        /** No repository offers the source (or it is not installed). */
        NOT_FOUND,

        /** The text does not load as a Mangayomi extension. */
        LOAD_FAILED,

        /** The store's files could not be written (disk full, no permission, …); nothing was changed. */
        STORAGE_FAILED,

        /** Any other failure (a bug); the message has the details. */
        UNEXPECTED,
    }
}
