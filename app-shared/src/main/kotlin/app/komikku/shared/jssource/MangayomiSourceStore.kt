package app.komikku.shared.jssource

import android.app.Application
import app.komikku.shared.jssource.JsSourceStoreException.Reason
import app.komikku.sourcejs.mihon.MangayomiJsRuntime
import app.komikku.sourcejs.mihon.MangayomiJsSource
import app.komikku.sourcejs.repo.JsSourceOffer
import app.komikku.sourcejs.repo.JsSourceStatus
import app.komikku.sourcejs.repo.MangayomiIndex
import app.komikku.sourcejs.repo.MangayomiIndexException
import app.komikku.sourcejs.repo.MangayomiRepoInfo
import app.komikku.sourcejs.repo.MangayomiVersions
import app.komikku.sourcejs.sha256Hex
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import exh.log.xLogE
import exh.log.xLogW
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * KMK: the Mangayomi JavaScript sources of the app (docs/kmp-desktop/PROGRESS.md, "I4"), on Android and desktop.
 *
 * - **Repositories** are index URLs in [JsSourcePreferences.repositories] (so the app-preference backup has them).
 *   [refresh] reads every index ([MangayomiIndex]: JavaScript manga entries only) and its optional `repo.json`; the
 *   offers are merged by id, the highest version winning ([JsSourceOffer.merge]). Nothing is read on its own and
 *   nothing is updated on its own.
 * - **Installed sources** are files under [directory]: `manifest.json` (an [InstalledJsSource] per source),
 *   `code/<sha256>.js` (texts, shared by entries of one file) and `icons/<id>`. Every write goes to a temporary file
 *   that is then renamed over the old one. A manifest that cannot be read is set aside (`manifest.json.corrupt-<time>`)
 *   and the store starts empty, with a warning in the log; no SQL is involved.
 * - **Sources** ([sources]) are built through [runtime] once the files are read (in the background: start-up never
 *   waits for the store) — a text that fails to load is reported in [items] and left out, never thrown. Sources marked
 *   NSFW are left out while [showNsfw] is false.
 * - **Trust:** only `https` URLs are fetched (repositories, code, icons; also after redirects); files have size limits
 *   ([Limits]). Installing is two steps: [download] fetches the text and gives its host and SHA-256 for the user to
 *   confirm, [install] fetches it again and installs it only if it still has the confirmed SHA-256. For installed
 *   sources whose version did not change, [refresh] also fetches the offered text, so a changed text shows as
 *   [JsSourceStatus.TEXT_CHANGED].
 *
 * @param client the app's HTTP client, read for every request.
 * @param allowUrl which URLs may be fetched; `https` only, except in tests (`MockWebServer` serves plain HTTP).
 */
class MangayomiSourceStore(
    val directory: File,
    private val preferences: JsSourcePreferences,
    val runtime: MangayomiJsRuntime,
    private val client: () -> OkHttpClient,
    showNsfw: Flow<Boolean> = flowOf(true),
    private val allowUrl: (HttpUrl) -> Boolean = { it.isHttps },
    private val limits: Limits = Limits(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    /** Largest files the store downloads, in bytes. */
    data class Limits(
        val indexBytes: Long = 8L * 1024 * 1024,
        val repoInfoBytes: Long = 64L * 1024,
        val codeBytes: Long = 4L * 1024 * 1024,
        val iconBytes: Long = 1L * 1024 * 1024,
    )

    /** The last read of one repository: its info and entries (kept from an earlier read when the last one failed). */
    private data class Listing(
        val info: MangayomiRepoInfo? = null,
        val entries: List<JsSourceOffer>? = null,
        val error: JsSourceStoreException? = null,
    )

    /** The SHA-256 of the text an offer's code URL served at the last [refresh]. */
    private data class RemoteText(val url: String, val version: String, val sha256: String)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val mutex = Mutex()

    private val manifestFile = File(directory, MANIFEST)
    private val codeDir = File(directory, "code")
    private val iconDir = File(directory, "icons")

    private val _installed = MutableStateFlow<List<InstalledJsSource>?>(null)

    /** The installed sources; `null` until the manifest was read. */
    val installed: StateFlow<List<InstalledJsSource>?> = _installed.asStateFlow()

    private val loaded = MutableStateFlow<Map<Long, MangayomiJsSource>>(emptyMap())
    private val loadErrors = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val listings = MutableStateFlow<Map<String, Listing>>(emptyMap())
    private val remoteTexts = MutableStateFlow<Map<Long, RemoteText>>(emptyMap())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)

    /** Whether the installed sources were read and built (or failed to build) once. */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val showNsfw: StateFlow<Boolean> = showNsfw.stateIn(scope, SharingStarted.Eagerly, true)

    /** The usable sources (installed, loaded, and not NSFW while those are hidden), for the source managers. */
    val sources: StateFlow<List<MangayomiJsSource>> = combine(loaded, this.showNsfw) { sources, nsfw ->
        sources.values.filter { nsfw || !it.descriptor.isNsfw }.sortedBy { it.id }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The added repositories, sorted by URL, with what their last read found. */
    val repositories: Flow<List<JsSourceRepository>> = combine(preferences.repositories().changes(), listings) { urls, listings ->
        urls.sorted().map { url ->
            val listing = listings[url]
            JsSourceRepository(
                url = url,
                name = listing?.info?.name,
                website = listing?.info?.website,
                entries = listing?.entries?.size,
                error = listing?.error,
            )
        }
    }

    /** One offer per id across the added repositories (the highest version). */
    val available: Flow<List<JsSourceOffer>> = combine(preferences.repositories().changes(), listings) { urls, listings ->
        offersOf(urls, listings)
    }

    /**
     * Installed sources (with their status) and offered ones not installed (NSFW ones only while shown), installed
     * first, each part by name.
     */
    val items: Flow<List<JsSourceItem>> = combine(
        _installed,
        available,
        combine(listings, remoteTexts, ::Pair),
        loadErrors,
        this.showNsfw,
    ) { installed, offers, (listings, texts), errors, nsfw ->
        val offerById = offers.associateBy { it.id }
        val installedItems = installed.orEmpty().map { source ->
            val offer = offerById[source.id]
            val text = texts[source.id]?.takeIf { offer != null && it.url == offer.descriptor.sourceCodeUrl && it.version == offer.descriptor.version }
            // Not obsolete while the repository it came from is added but was not read yet (or never read well).
            val listingKnown = source.repoUrl !in preferences.repositories().get() || listings[source.repoUrl]?.entries != null
            JsSourceItem(
                installed = source,
                offer = offer,
                status = JsSourceStatus.of(source.descriptor.version, source.sha256, offer?.descriptor, text?.sha256, listingKnown),
                loadError = errors[source.id],
            )
        }
        val installedIds = installedItems.map { it.id }.toSet()
        val offered = offers
            .filter { it.id !in installedIds && (nsfw || !it.descriptor.isNsfw) }
            .map { JsSourceItem(installed = null, offer = it, status = null) }
        installedItems.sortedBy { it.descriptor.name.lowercase() } + offered.sortedBy { it.descriptor.name.lowercase() }
    }

    init {
        scope.launch { load() }
    }

    // ---- Repositories ----

    /**
     * Adds the repository whose index is at [url] after reading it once (a URL that is not `https`, cannot be read or
     * is no Mangayomi index is not added). Adding one again reads it again.
     *
     * @return the normalised URL that was stored.
     * @throws JsSourceStoreException
     */
    suspend fun addRepository(url: String): String {
        val normalized = url.trim()
        checkedUrl(normalized)
        val listing = readListing(normalized)
        listings.update { it + (normalized to listing) }
        val repos = preferences.repositories()
        repos.set(repos.get() + normalized)
        checkInstalledTexts()
        return normalized
    }

    /** Removes the repository [url]; its sources stay installed (and become obsolete when nothing else offers them). */
    fun removeRepository(url: String) {
        val repos = preferences.repositories()
        repos.set(repos.get() - url)
        listings.update { it - url }
    }

    /**
     * Reads every added repository again (a failed read keeps the entries of the last good one and records the
     * error), then checks whether the texts of installed sources changed under the same version.
     */
    suspend fun refresh() {
        if (!_isRefreshing.compareAndSet(expect = false, update = true)) {
            _isRefreshing.first { !it }
            return
        }
        try {
            val urls = preferences.repositories().get()
            val results = coroutineScope {
                urls.map { url ->
                    async {
                        url to try {
                            readListing(url)
                        } catch (e: JsSourceStoreException) {
                            xLogW("Could not read the JavaScript source repository $url", e)
                            Listing(error = e)
                        }
                    }
                }.awaitAll()
            }
            listings.update { current ->
                results.associate { (url, listing) ->
                    val previous = current[url]
                    url to if (listing.error != null && previous != null) previous.copy(error = listing.error) else listing
                }
            }
            checkInstalledTexts()
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun readListing(url: String): Listing {
        val text = fetchText(url, limits.indexBytes)
        val index = try {
            MangayomiIndex.parse(text)
        } catch (e: MangayomiIndexException) {
            throw JsSourceStoreException(Reason.INVALID_INDEX, "$url is no Mangayomi index: ${e.message}", e)
        }
        val info = try {
            MangayomiRepoInfo.parse(fetchText(MangayomiRepoInfo.infoUrl(url), limits.repoInfoBytes))
        } catch (e: JsSourceStoreException) {
            null
        }
        return Listing(info = info, entries = index.entries.map { JsSourceOffer(it, url) })
    }

    private fun offersOf(urls: Set<String>, listings: Map<String, Listing>): List<JsSourceOffer> =
        JsSourceOffer.merge(urls.sorted().mapNotNull { url -> listings[url]?.entries?.let { url to it.map(JsSourceOffer::descriptor) } })

    /** The best offer for [id] from the last reads, if any. */
    fun offer(id: Long): JsSourceOffer? = offersOf(preferences.repositories().get(), listings.value).find { it.id == id }

    /** For installed sources whose version is still offered: the SHA-256 of the text the offer serves now. */
    private suspend fun checkInstalledTexts() {
        val installed = _installed.value.orEmpty()
        val checks = installed.mapNotNull { source ->
            val offer = offer(source.id) ?: return@mapNotNull null
            if (MangayomiVersions.compare(source.descriptor.version, offer.descriptor.version) != 0) return@mapNotNull null
            source.id to offer.descriptor
        }
        val found = checks.mapNotNull { (id, descriptor) ->
            try {
                val text = fetchText(descriptor.sourceCodeUrl, limits.codeBytes)
                id to RemoteText(descriptor.sourceCodeUrl, descriptor.version, sha256Hex(text))
            } catch (e: JsSourceStoreException) {
                xLogW("Could not check the text of JavaScript source ${descriptor.name}", e)
                null
            }
        }
        remoteTexts.update { it + found }
    }

    // ---- Install / update / uninstall ----

    /**
     * Downloads the text [offer] points to, for the user to confirm its host and SHA-256 before [install].
     *
     * @throws JsSourceStoreException
     */
    suspend fun download(offer: JsSourceOffer): JsSourceDownload {
        val url = checkedUrl(offer.descriptor.sourceCodeUrl)
        val text = fetchText(offer.descriptor.sourceCodeUrl, limits.codeBytes)
        val sha256 = sha256Hex(text)
        if (_installed.value.orEmpty().any { it.id == offer.id }) {
            remoteTexts.update { it + (offer.id to RemoteText(offer.descriptor.sourceCodeUrl, offer.descriptor.version, sha256)) }
        }
        return JsSourceDownload(
            offer = offer,
            host = url.host,
            sha256 = sha256,
            sizeBytes = text.encodeToByteArray().size.toLong(),
            installed = _installed.value.orEmpty().find { it.id == offer.id },
        )
    }

    /** [download] of the best offer for the installed source [id] (an update, or the same version's new text). */
    suspend fun downloadUpdate(id: Long): JsSourceDownload =
        download(offer(id) ?: throw JsSourceStoreException(Reason.NOT_FOUND, "No repository offers the JavaScript source $id"))

    /**
     * Installs (or updates to) the text of [offer] if it still has [confirmedSha256]: downloads it, checks the hash,
     * loads it (a text that does not load is not installed; an update keeps the old version then), stores the text,
     * icon and manifest, and publishes the source.
     *
     * @throws JsSourceStoreException
     */
    suspend fun install(offer: JsSourceOffer, confirmedSha256: String): MangayomiJsSource {
        val descriptor = offer.descriptor
        val text = fetchText(descriptor.sourceCodeUrl, limits.codeBytes)
        val sha256 = sha256Hex(text)
        if (!sha256.equals(confirmedSha256.trim(), ignoreCase = true)) {
            throw JsSourceStoreException(
                Reason.SHA_MISMATCH,
                "The text of ${descriptor.name} changed since it was confirmed (SHA-256 $sha256, confirmed $confirmedSha256)",
            )
        }
        return mutex.withLock {
            awaitManifest()
            val source = try {
                runtime.source(descriptor, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw JsSourceStoreException(Reason.LOAD_FAILED, "${descriptor.name} does not load: ${e.message}", e)
            }
            withContext(Dispatchers.IO) {
                writeAtomically(codeFile(sha256), text.encodeToByteArray())
                downloadIcon(descriptor.id, descriptor.iconUrl)
            }
            val now = clock()
            val current = _installed.value.orEmpty()
            val previous = current.find { it.id == descriptor.id }
            val record = InstalledJsSource(
                descriptor = descriptor,
                repoUrl = offer.repoUrl,
                sha256 = sha256,
                installedAt = previous?.installedAt ?: now,
                updatedAt = now,
                metadata = source.metadata,
            )
            val updated = current.filterNot { it.id == descriptor.id } + record
            writeManifest(updated)
            _installed.value = updated
            if (previous != null && previous.sha256 != sha256) deleteUnusedCode(previous.sha256, updated)
            loaded.update { it + (descriptor.id to source) }
            loadErrors.update { it - descriptor.id }
            remoteTexts.update { it + (descriptor.id to RemoteText(descriptor.sourceCodeUrl, descriptor.version, sha256)) }
            source
        }
    }

    /** [install] for an update of the installed source [id] to its best offer. */
    suspend fun update(id: Long, confirmedSha256: String): MangayomiJsSource =
        install(offer(id) ?: throw JsSourceStoreException(Reason.NOT_FOUND, "No repository offers the JavaScript source $id"), confirmedSha256)

    /** Removes the installed source [id]: its source, text (unless another entry uses it) and icon; its preferences stay. */
    suspend fun uninstall(id: Long) {
        mutex.withLock {
            awaitManifest()
            val current = _installed.value.orEmpty()
            val record = current.find { it.id == id } ?: return
            val updated = current - record
            writeManifest(updated)
            _installed.value = updated
            loaded.update { it - id }
            loadErrors.update { it - id }
            remoteTexts.update { it - id }
            runtime.remove(record.descriptor)
            withContext(Dispatchers.IO) {
                deleteUnusedCode(record.sha256, updated)
                iconFile(id).delete()
            }
        }
    }

    // ---- Lookups ----

    /** The installed record of the Mihon source [sourceId], if it is a JavaScript source. */
    fun installedSource(sourceId: Long): InstalledJsSource? = _installed.value?.find { it.sourceId == sourceId }

    /** The stored icon of the Mihon source [sourceId], if it is an installed JavaScript source with an icon. */
    fun iconFileForSource(sourceId: Long): File? =
        installedSource(sourceId)?.let { iconFile(it.id) }?.takeIf { it.isFile && it.length() > 0 }

    /** Closes the sources and stops background work. */
    fun close() {
        scope.cancel()
        runtime.close()
    }

    // ---- Files ----

    private suspend fun awaitManifest() {
        if (_installed.value == null) _installed.first { it != null }
    }

    private suspend fun load() {
        val records = mutex.withLock { withContext(Dispatchers.IO) { readManifest() } }
        _installed.value = records
        val sources = mutableMapOf<Long, MangayomiJsSource>()
        val errors = mutableMapOf<Long, String>()
        val withMetadata = mutableListOf<InstalledJsSource>()
        records.forEach { record ->
            try {
                val text = withContext(Dispatchers.IO) { codeFile(record.sha256).takeIf(File::isFile)?.readText() }
                    ?: throw IOException("The text file of ${record.descriptor.name} is missing")
                if (!sha256Hex(text).equals(record.sha256, ignoreCase = true)) {
                    throw IOException("The text file of ${record.descriptor.name} does not match its SHA-256")
                }
                val source = runtime.source(record.descriptor, text, record.metadata)
                // The cached metadata may predate preference changes: read it again in the background.
                if (record.metadata != null) source.requestMetadataRefresh() else withMetadata += record.copy(metadata = source.metadata)
                sources[record.id] = source
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                xLogE("Could not load the JavaScript source ${record.descriptor.name} (${record.id})", e)
                errors[record.id] = e.message ?: e.toString()
            }
        }
        loaded.value = sources
        loadErrors.value = errors
        _isLoaded.value = true
        if (withMetadata.isNotEmpty()) {
            mutex.withLock {
                val byId = withMetadata.associateBy { it.id }
                val updated = _installed.value.orEmpty().map { current ->
                    byId[current.id]?.takeIf { it.sha256 == current.sha256 } ?: current
                }
                runCatching { writeManifest(updated) }.onFailure { xLogW("Could not cache JavaScript source metadata", it) }
                _installed.value = updated
            }
        }
    }

    private fun readManifest(): List<InstalledJsSource> {
        if (!manifestFile.isFile) return emptyList()
        return try {
            val manifest = json.decodeFromString(JsSourceManifest.serializer(), manifestFile.readText())
            manifest.sources.distinctBy { it.id }
        } catch (e: Exception) {
            val aside = File(directory, "$MANIFEST.corrupt-${clock()}")
            val kept = manifestFile.renameTo(aside)
            xLogW("The JavaScript source manifest could not be read; starting empty (kept as ${aside.name}: $kept)", e)
            emptyList()
        }
    }

    private suspend fun writeManifest(sources: List<InstalledJsSource>) = withContext(Dispatchers.IO) {
        val text = json.encodeToString(JsSourceManifest.serializer(), JsSourceManifest(sources = sources.sortedBy { it.id }))
        writeAtomically(manifestFile, text.encodeToByteArray())
    }

    private fun codeFile(sha256: String): File = File(codeDir, "${sha256.lowercase()}.js")

    private fun iconFile(id: Long): File = File(iconDir, id.toString())

    private fun deleteUnusedCode(sha256: String, installed: List<InstalledJsSource>) {
        if (installed.none { it.sha256.equals(sha256, ignoreCase = true) }) codeFile(sha256).delete()
    }

    /** Best effort: a missing, insecure or too large icon only means no icon. */
    private suspend fun downloadIcon(id: Long, iconUrl: String) {
        val file = iconFile(id)
        if (iconUrl.isBlank()) {
            file.delete()
            return
        }
        try {
            writeAtomically(file, fetchBytes(iconUrl, limits.iconBytes))
        } catch (e: JsSourceStoreException) {
            xLogW("No icon for JavaScript source $id", e)
        }
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // ---- HTTP ----

    private fun checkedUrl(url: String): HttpUrl {
        val parsed = url.trim().toHttpUrlOrNull()
        if (parsed == null || !allowUrl(parsed)) {
            throw JsSourceStoreException(Reason.INSECURE_URL, "Only https URLs are allowed: $url")
        }
        return parsed
    }

    private suspend fun fetchText(url: String, limit: Long): String = fetchBytes(url, limit).decodeToString()

    /**
     * GET [url] (no cache) into memory, at most [limit] bytes. Redirects are followed here, at most
     * [MAX_REDIRECTS], each target checked by [allowUrl] before it is requested.
     */
    private suspend fun fetchBytes(url: String, limit: Long): ByteArray = withContext(Dispatchers.IO) {
        val noRedirects = client().newBuilder().followRedirects(false).followSslRedirects(false).build()
        var target = checkedUrl(url)
        repeat(MAX_REDIRECTS + 1) { _ ->
            val response = try {
                noRedirects.newCall(GET(target, cache = CacheControl.FORCE_NETWORK)).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw JsSourceStoreException(Reason.DOWNLOAD_FAILED, "Could not download $url: ${e.message}", e)
            }
            response.use {
                if (it.isRedirect) {
                    val location = it.header("Location")?.let(it.request.url::resolve)
                        ?: throw JsSourceStoreException(Reason.DOWNLOAD_FAILED, "Could not download $url: bad redirect")
                    if (!allowUrl(location)) {
                        throw JsSourceStoreException(Reason.INSECURE_URL, "$url redirected to $location, which is not https")
                    }
                    target = location
                    return@use null
                }
                if (!it.isSuccessful) {
                    throw JsSourceStoreException(Reason.DOWNLOAD_FAILED, "Could not download $url: HTTP ${it.code}")
                }
                val body = it.body
                if (body.contentLength() > limit) {
                    throw JsSourceStoreException(Reason.TOO_LARGE, "$url is larger than $limit bytes")
                }
                try {
                    val source = body.source()
                    if (source.request(limit + 1)) throw JsSourceStoreException(Reason.TOO_LARGE, "$url is larger than $limit bytes")
                    source.buffer.readByteArray()
                } catch (e: IOException) {
                    throw JsSourceStoreException(Reason.DOWNLOAD_FAILED, "Could not download $url: ${e.message}", e)
                }
            }?.let { return@withContext it }
        }
        throw JsSourceStoreException(Reason.DOWNLOAD_FAILED, "Could not download $url: more than $MAX_REDIRECTS redirects")
    }

    companion object {
        const val DIRECTORY = "mangayomi-js"
        const val MANIFEST = "manifest.json"

        /** Redirects followed by one download. */
        const val MAX_REDIRECTS = 5

        /**
         * The app's store (Android and desktop DI): files under `<filesDir>/mangayomi-js`, one runtime on the app's
         * client and preference files, NSFW sources following [SourcePreferences.showNsfwSource].
         */
        fun forApp(
            application: Application,
            network: NetworkHelper,
            preferences: JsSourcePreferences,
            sourcePreferences: SourcePreferences,
        ): MangayomiSourceStore = MangayomiSourceStore(
            directory = File(application.filesDir, DIRECTORY),
            preferences = preferences,
            runtime = MangayomiJsRuntime(application, network),
            client = { network.client },
            showNsfw = sourcePreferences.showNsfwSource().changes(),
        )

        /**
         * [MangayomiSourceStore.sources] of the store [store] gives, or no sources when the store cannot be built, so
         * the source managers keep working without JavaScript sources.
         */
        fun sourcesOrEmpty(store: () -> MangayomiSourceStore): StateFlow<List<MangayomiJsSource>> = try {
            store().sources
        } catch (e: Throwable) {
            xLogE("The JavaScript source store could not be started; no JavaScript sources", e)
            MutableStateFlow(emptyList())
        }
    }
}
