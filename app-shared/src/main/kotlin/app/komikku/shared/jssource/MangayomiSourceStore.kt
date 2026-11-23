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
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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
 * - **Sources** ([sources]) are built through [runtime] once the files are read (in the background; the source
 *   managers wait for that through [sourcesOrEmpty], at most [LOAD_TIMEOUT_MILLIS]) — a text that fails to load is
 *   reported in [items] and left out, never thrown. Sources marked NSFW are left out while [showNsfw] is false.
 * - **Failures** are [JsSourceStoreException]s, file errors included ([Reason.STORAGE_FAILED]); an install, update or
 *   uninstall that fails changes nothing (the installed version stays in use).
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
    val sources: StateFlow<List<MangayomiJsSource>> = combine(loaded, this.showNsfw, ::usable)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * [sources], or `null` until the installed sources were loaded (or failed to load) once. The current values are
     * read when [isLoaded] turns true (the loaded sources are published before it), so the first list is complete.
     */
    val loadedSources: StateFlow<List<MangayomiJsSource>?> = combine(loaded, this.showNsfw, _isLoaded) { _, _, isLoaded ->
        if (isLoaded) usable(loaded.value, this.showNsfw.value) else null
    }.stateIn(scope, SharingStarted.Eagerly, null)

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
        combine(preferences.repositories().changes(), listings, ::Pair),
        remoteTexts,
        loadErrors,
        this.showNsfw,
    ) { installed, (urls, listings), texts, errors, nsfw ->
        val offers = offersOf(urls, listings)
        val offerById = offers.associateBy { it.id }
        // Not obsolete while any added repository was not read yet (or never read well): it may offer the source.
        val listingKnown = urls.all { listings[it]?.entries != null }
        val installedItems = installed.orEmpty().map { source ->
            val offer = offerById[source.id]
            val text = texts[source.id]?.takeIf { offer != null && it.url == offer.descriptor.sourceCodeUrl && it.version == offer.descriptor.version }
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

    private fun usable(sources: Map<Long, MangayomiJsSource>, nsfw: Boolean): List<MangayomiJsSource> =
        sources.values.filter { nsfw || !it.descriptor.isNsfw }.sortedBy { it.id }

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
        val repos = preferences.repositories()
        // The URL first: a refresh finishing in between keeps the listings of added repositories only.
        repos.set(repos.get() + normalized)
        listings.update { if (normalized in repos.get()) it + (normalized to listing) else it }
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
            // Merged into the current listings, for the repositories added now: one added while this refresh ran keeps
            // its listing, one removed meanwhile does not come back.
            val added = preferences.repositories().get()
            listings.update { current ->
                val merged = current.filterKeys { it in added }.toMutableMap()
                results.forEach { (url, listing) ->
                    if (url !in added) return@forEach
                    val previous = current[url]
                    merged[url] = if (listing.error != null && previous != null) previous.copy(error = listing.error) else listing
                }
                merged
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
        awaitLoaded()
        val url = checkedUrl(offer.descriptor.sourceCodeUrl)
        val fetched = fetchBytes(offer.descriptor.sourceCodeUrl, limits.codeBytes)
        val text = fetched.bytes.decodeToString()
        val sha256 = sha256Hex(text)
        if (_installed.value.orEmpty().any { it.id == offer.id }) {
            remoteTexts.update { it + (offer.id to RemoteText(offer.descriptor.sourceCodeUrl, offer.descriptor.version, sha256)) }
        }
        return JsSourceDownload(
            offer = offer,
            host = fetched.url.host,
            sha256 = sha256,
            sizeBytes = text.encodeToByteArray().size.toLong(),
            installed = _installed.value.orEmpty().find { it.id == offer.id },
            requestedHost = url.host,
        )
    }

    /** [download] of the best offer for the installed source [id] (an update, or the same version's new text). */
    suspend fun downloadUpdate(id: Long): JsSourceDownload =
        download(offer(id) ?: throw JsSourceStoreException(Reason.NOT_FOUND, "No repository offers the JavaScript source $id"))

    /**
     * Installs (or updates to) the text of [offer] if it still has [confirmedSha256]: downloads it, checks the hash,
     * loads it next to the installed version, stores the text and manifest, then swaps the new source in and stores
     * its icon. Any failure before the manifest is written (a text that does not load, a file that cannot be written)
     * changes nothing: the installed version stays in use and the new files are removed.
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
        awaitLoaded()
        return mutex.withLock {
            // Built next to the installed version, which stays registered and open until the swap below.
            val source = try {
                runtime.prepare(descriptor, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw JsSourceStoreException(Reason.LOAD_FAILED, "${descriptor.name} does not load: ${e.message}", e)
            }
            val icon = fetchIcon(descriptor.id, descriptor.iconUrl)
            val current = _installed.value.orEmpty()
            val previous = current.find { it.id == descriptor.id }
            val code = codeFile(sha256)
            val codeWasThere = code.isFile
            val updated = try {
                storage("store the text of ${descriptor.name}") {
                    withContext(Dispatchers.IO) { writeAtomically(code, text.encodeToByteArray()) }
                }
                val now = clock()
                val record = InstalledJsSource(
                    descriptor = descriptor,
                    repoUrl = offer.repoUrl,
                    sha256 = sha256,
                    installedAt = previous?.installedAt ?: now,
                    updatedAt = now,
                    metadata = source.metadata,
                )
                val updated = current.filterNot { it.id == descriptor.id } + record
                storage("store the JavaScript source manifest") { writeManifest(updated) }
                updated
            } catch (e: Throwable) {
                source.close()
                if (!codeWasThere) withContext(NonCancellable + Dispatchers.IO) { code.delete() }
                throw e
            }
            // Written: from here on the new version is the installed one, also if the caller is cancelled.
            withContext(NonCancellable) {
                _installed.value = updated
                runtime.register(source)
                loaded.update { it + (descriptor.id to source) }
                loadErrors.update { it - descriptor.id }
                remoteTexts.update { it + (descriptor.id to RemoteText(descriptor.sourceCodeUrl, descriptor.version, sha256)) }
                withContext(Dispatchers.IO) {
                    if (previous != null && previous.sha256 != sha256) deleteUnusedCode(previous.sha256, updated)
                    storeIcon(descriptor.id, icon)
                }
            }
            source
        }
    }

    /** [install] for an update of the installed source [id] to its best offer. */
    suspend fun update(id: Long, confirmedSha256: String): MangayomiJsSource =
        install(offer(id) ?: throw JsSourceStoreException(Reason.NOT_FOUND, "No repository offers the JavaScript source $id"), confirmedSha256)

    /** Removes the installed source [id]: its source, text (unless another entry uses it) and icon; its preferences stay. */
    suspend fun uninstall(id: Long) {
        awaitLoaded()
        mutex.withLock {
            val current = _installed.value.orEmpty()
            val record = current.find { it.id == id } ?: return
            val updated = current - record
            storage("store the JavaScript source manifest") { writeManifest(updated) }
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

    /** Waits for [load] (outside the lock, which [load] holds). */
    private suspend fun awaitLoaded() {
        if (!_isLoaded.value) _isLoaded.first { it }
    }

    /**
     * Reads the manifest and builds every installed source, under the store's lock: an install, update or uninstall
     * started meanwhile waits, so it can neither be overwritten by the start-up state nor race it in the runtime.
     */
    private suspend fun load() = mutex.withLock {
        try {
            loadLocked()
        } finally {
            if (_installed.value == null) _installed.value = emptyList()
            _isLoaded.value = true
        }
    }

    private suspend fun loadLocked() {
        val records = withContext(Dispatchers.IO) { readManifest() }
        _installed.value = records
        val sources = mutableMapOf<Long, MangayomiJsSource>()
        val errors = mutableMapOf<Long, String>()
        var metadataCached = false
        val updated = records.map { record ->
            try {
                val text = withContext(Dispatchers.IO) { codeFile(record.sha256).takeIf(File::isFile)?.readText() }
                    ?: throw IOException("The text file of ${record.descriptor.name} is missing")
                if (!sha256Hex(text).equals(record.sha256, ignoreCase = true)) {
                    throw IOException("The text file of ${record.descriptor.name} does not match its SHA-256")
                }
                val source = runtime.source(record.descriptor, text, record.metadata)
                sources[record.id] = source
                if (record.metadata != null) {
                    // The cached metadata may predate preference changes: read it again in the background.
                    source.requestMetadataRefresh()
                    record
                } else {
                    metadataCached = true
                    record.copy(metadata = source.metadata)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                xLogE("Could not load the JavaScript source ${record.descriptor.name} (${record.id})", e)
                errors[record.id] = e.message ?: e.toString()
                record
            }
        }
        loaded.value = sources
        loadErrors.value = errors
        if (metadataCached) {
            try {
                writeManifest(updated)
                _installed.value = updated
            } catch (e: IOException) {
                xLogW("Could not cache the JavaScript source metadata", e)
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

    /** Best effort: a missing, insecure or too large icon only means no icon (`null`). */
    private suspend fun fetchIcon(id: Long, iconUrl: String): ByteArray? {
        if (iconUrl.isBlank()) return null
        return try {
            fetchBytes(iconUrl, limits.iconBytes).bytes
        } catch (e: JsSourceStoreException) {
            xLogW("No icon for JavaScript source $id", e)
            null
        }
    }

    /** Stores [bytes] as the icon of [id]; without an icon (or when it cannot be written) an older one is removed. */
    private fun storeIcon(id: Long, bytes: ByteArray?) {
        val file = iconFile(id)
        if (bytes != null) {
            try {
                writeAtomically(file, bytes)
                return
            } catch (e: IOException) {
                xLogW("Could not store the icon of JavaScript source $id", e)
            }
        }
        file.delete()
    }

    /** Runs the file operation [block], reporting a file failure as [Reason.STORAGE_FAILED]. */
    private inline fun <T> storage(what: String, block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw JsSourceStoreException(Reason.STORAGE_FAILED, "Could not $what: ${e.message}", e)
    } catch (e: SecurityException) {
        throw JsSourceStoreException(Reason.STORAGE_FAILED, "Could not $what: ${e.message}", e)
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.outputStream().use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            temp.delete()
            throw e
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

    private suspend fun fetchText(url: String, limit: Long): String = fetchBytes(url, limit).bytes.decodeToString()

    /** A downloaded file and the [url] that served it (after redirects). */
    private class Fetched(val url: HttpUrl, val bytes: ByteArray)

    /**
     * GET [url] (no cache) into memory, at most [limit] bytes. Redirects are followed here, at most
     * [MAX_REDIRECTS], each target checked by [allowUrl] before it is requested.
     */
    private suspend fun fetchBytes(url: String, limit: Long): Fetched = withContext(Dispatchers.IO) {
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
                    Fetched(target, source.buffer.readByteArray())
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

        /** How long the source managers wait for the installed sources at start-up ([sourcesOrEmpty]). */
        const val LOAD_TIMEOUT_MILLIS = 15_000L

        /**
         * The sources of the store [store] gives, for the source managers: the first list comes once the installed
         * sources were loaded ([loadedSources]), so a manager that waits for it before it counts as initialized does
         * not take an installed source for a missing one (a queued download, the download cache, the reader). The
         * wait is bounded: after [loadTimeoutMillis] the first list is empty (logged) and the sources follow when
         * loaded. A store that cannot be built counts as loaded without sources, so the managers keep working.
         */
        fun sourcesOrEmpty(
            loadTimeoutMillis: Long = LOAD_TIMEOUT_MILLIS,
            store: () -> MangayomiSourceStore,
        ): Flow<List<MangayomiJsSource>> {
            val sources = try {
                store().loadedSources
            } catch (e: Throwable) {
                xLogE("The JavaScript source store could not be started; no JavaScript sources", e)
                return flowOf(emptyList())
            }
            return flow {
                if (withTimeoutOrNull(loadTimeoutMillis) { sources.first { it != null } } == null) {
                    xLogW("The JavaScript sources were not loaded after $loadTimeoutMillis ms; starting without them")
                    emit(emptyList())
                }
                emitAll(sources.filterNotNull())
            }
        }

        /** The app's store (DI), or `null` when it cannot be built (logged), for the screens that show it. */
        fun getOrNull(): MangayomiSourceStore? = try {
            Injekt.get<MangayomiSourceStore>()
        } catch (e: Throwable) {
            xLogE("The JavaScript source store could not be started", e)
            null
        }
    }
}
