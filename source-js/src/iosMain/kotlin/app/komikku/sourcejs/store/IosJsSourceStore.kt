package app.komikku.sourcejs.store

import app.komikku.sourcejs.IosJsSourceRuntime
import app.komikku.sourcejs.JsSourceDescriptor
import app.komikku.sourcejs.bridge.DarwinHostHttp
import app.komikku.sourcejs.bridge.HostHttp
import app.komikku.sourcejs.bridge.HostHttpRequest
import app.komikku.sourcejs.bridge.HttpBridge
import app.komikku.sourcejs.model.JsPreference
import app.komikku.sourcejs.model.JsSourceMetadata
import app.komikku.sourcejs.repo.JsSourceOffer
import app.komikku.sourcejs.repo.JsSourceStatus
import app.komikku.sourcejs.repo.MangayomiIndex
import app.komikku.sourcejs.repo.MangayomiIndexException
import app.komikku.sourcejs.repo.MangayomiRepoInfo
import app.komikku.sourcejs.repo.MangayomiVersions
import app.komikku.sourcejs.sha256Hex
import app.komikku.sourcejs.store.JsSourceStoreException.Reason
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

/**
 * KMK: the Mangayomi JavaScript sources installed in an iOS app (docs/kmp-desktop/IOS_CI.md, "I5b"), the iOS twin of
 * app-shared's JVM `MangayomiSourceStore`, with the same behaviour, states and file layout.
 *
 * - **Repositories** are index URLs, kept in [repositoryDefaults] under [REPOSITORIES_KEY] (the JVM preference key, so
 *   a later settings backup maps one to the other). [refresh] reads every index ([MangayomiIndex]: JavaScript manga
 *   entries only) and its optional `repo.json`; the offers are merged by id, the highest version winning
 *   ([JsSourceOffer.merge]). Nothing is read on its own and nothing is updated on its own.
 * - **Installed sources** are files under [root] (by default `<Application Support>/mangayomi-js`): `manifest.json` (an
 *   [InstalledJsSource] per source, with its cached [JsSourceMetadata]), `code/<sha256>.js` (texts, shared by entries of
 *   one file) and `icons/<id>`. Every write goes to a temporary file that is synced and then renamed over the old one.
 *   A manifest that cannot be read is set aside (`manifest.json.corrupt-<time>`) and the store starts empty, with a
 *   warning in the log; a code file that is missing or does not match its SHA-256 is a load error of that source.
 * - **Sources** ([sources]) are [IosJsSource]s built through [runtime] once the files are read, without running the
 *   scripts: the cached metadata types each source's preferences ([declaredPreferences], which [runtime] is given).
 *   Sources marked NSFW are left out while `showNsfw` is false.
 * - **Failures** are [JsSourceStoreException]s, file errors included ([Reason.STORAGE_FAILED]); an install, update or
 *   uninstall that fails changes nothing (the installed version stays in use).
 * - **Trust:** only `https` URLs are fetched (repositories, code, icons; also after redirects, which the store follows
 *   itself, at most [MAX_REDIRECTS]); files have size limits ([Limits]). Installing is two steps: [download] fetches the
 *   text and gives its host and SHA-256 for the user to confirm, [install] fetches it again and installs it only if it
 *   still has the confirmed SHA-256. For installed sources whose version did not change, [refresh] also fetches the
 *   offered text, so a changed text shows as [JsSourceStatus.TEXT_CHANGED].
 *
 * Differences from the JVM store: no Mihon source adapter (the app calls [IosJsSource.extension]); [uninstall] also
 * deletes the source's preferences ([IosJsSourceRuntime.clearPreferences]); the metadata is read again only through
 * [refreshMetadata] (the JVM adapter re-reads it in the background).
 *
 * @param root the store's directory; tests pass a temporary one.
 * @param http the HTTP client for the downloads and, by default, for the sources; `null` creates a [DarwinHostHttp]
 *   that [close] closes.
 * @param runtime builds the store's [IosJsSourceRuntime] from the store's [declaredPreferences]; by default one on
 *   the same HTTP client.
 * @param allowUrl which URLs may be fetched; `https` only by default.
 * @param log the store's warnings (the device log by default).
 */
class IosJsSourceStore(
    val root: String = StoreFiles.applicationSupport(DIRECTORY),
    http: HostHttp? = null,
    private val repositoryDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    runtime: ((declaredPreferences: (JsSourceDescriptor) -> List<JsPreference>) -> IosJsSourceRuntime)? = null,
    showNsfw: Flow<Boolean> = flowOf(true),
    private val allowUrl: (Url) -> Boolean = { it.protocol.name == "https" },
    private val limits: Limits = Limits(),
    private val clock: () -> Long = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
    private val log: (String) -> Unit = { NSLog("[JS store] $it".replace("%", "%%")) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** Largest files the store downloads, in bytes, and the time limit of one download. */
    data class Limits(
        val indexBytes: Long = 8L * 1024 * 1024,
        val repoInfoBytes: Long = 64L * 1024,
        val codeBytes: Long = 4L * 1024 * 1024,
        val iconBytes: Long = 1L * 1024 * 1024,
        val timeoutMillis: Long = 120_000,
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

    private val manifestPath = "$root/$MANIFEST"
    private val codeDir = "$root/code"
    private val iconDir = "$root/icons"

    private val _installed = MutableStateFlow<List<InstalledJsSource>?>(null)

    /** The installed sources; `null` until the manifest was read. */
    val installed: StateFlow<List<InstalledJsSource>?> = _installed.asStateFlow()

    private val loaded = MutableStateFlow<Map<Long, IosJsSource>>(emptyMap())
    private val loadErrors = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val listings = MutableStateFlow<Map<String, Listing>>(emptyMap())
    private val remoteTexts = MutableStateFlow<Map<Long, RemoteText>>(emptyMap())
    private val repositoryUrls = MutableStateFlow(readRepositories())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)

    /** Whether the installed sources were read (or failed to read) once. */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val ownedHttp: DarwinHostHttp? = if (http == null) DarwinHostHttp() else null
    private val http: HostHttp = http ?: ownedHttp!!

    /** The host the sources run in; its preferences are typed by [declaredPreferences]. Closed by [close]. */
    val runtime: IosJsSourceRuntime = runtime?.invoke(::declaredPreferences)
        ?: IosJsSourceRuntime(http = this.http, declaredPreferences = ::declaredPreferences)

    private val showNsfw: StateFlow<Boolean> = showNsfw.stateIn(scope, SharingStarted.Eagerly, true)

    /** The usable sources (installed, loaded, and not NSFW while those are hidden), by id. */
    val sources: StateFlow<List<IosJsSource>> = combine(loaded, this.showNsfw, ::usable)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The added repositories, sorted by URL, with what their last read found. */
    val repositories: Flow<List<JsSourceRepository>> = combine(repositoryUrls, listings) { urls, listings ->
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
    val available: Flow<List<JsSourceOffer>> = combine(repositoryUrls, listings, ::offersOf)

    /**
     * Installed sources (with their status) and offered ones not installed (NSFW ones only while shown), installed
     * first, each part by name.
     */
    val items: Flow<List<JsSourceItem>> = combine(
        _installed,
        combine(repositoryUrls, listings, ::Pair),
        remoteTexts,
        loadErrors,
        this.showNsfw,
    ) { installed, (urls, listings), texts, errors, nsfw ->
        val offers = offersOf(urls, listings)
        val offerById = offers.associateBy { it.id }
        // Not obsolete while any added repository was not read yet (or never read well), nor with no repository.
        val listingKnown = urls.isNotEmpty() && urls.all { listings[it]?.entries != null }
        val installedItems = installed.orEmpty().map { source ->
            val offer = offerById[source.id]
            val text = texts[source.id]?.takeIf {
                offer != null && it.url == offer.descriptor.sourceCodeUrl && it.version == offer.descriptor.version
            }
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

    private fun usable(sources: Map<Long, IosJsSource>, nsfw: Boolean): List<IosJsSource> =
        sources.values.filter { nsfw || !it.descriptor.isNsfw }.sortedBy { it.id }

    /**
     * The declared preferences of the installed [descriptor] from its cached metadata (empty when unknown), for
     * [runtime]'s `NSUserDefaults` typing: known without running the script.
     */
    fun declaredPreferences(descriptor: JsSourceDescriptor): List<JsPreference> =
        _installed.value?.find { it.id == descriptor.id }?.metadata?.preferences.orEmpty()

    // ---- Repositories ----

    private fun readRepositories(): Set<String> =
        repositoryDefaults.arrayForKey(REPOSITORIES_KEY)?.filterIsInstance<String>()?.toSet().orEmpty()

    private fun updateRepositories(change: (Set<String>) -> Set<String>): Set<String> {
        val urls = repositoryUrls.updateAndGet(change)
        repositoryDefaults.setObject(urls.sorted(), forKey = REPOSITORIES_KEY)
        return urls
    }

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
        // The URL first: a refresh finishing in between keeps the listings of added repositories only.
        val urls = updateRepositories { it + normalized }
        listings.update { if (normalized in urls) it + (normalized to listing) else it }
        checkInstalledTexts()
        return normalized
    }

    /** Removes the repository [url]; its sources stay installed (and become obsolete unless another one offers them). */
    fun removeRepository(url: String) {
        updateRepositories { it - url }
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
            val urls = repositoryUrls.value
            val results = coroutineScope {
                urls.map { url ->
                    async {
                        url to try {
                            readListing(url)
                        } catch (e: JsSourceStoreException) {
                            log("Could not read the JavaScript source repository $url: ${e.message}")
                            Listing(error = e)
                        }
                    }
                }.awaitAll()
            }
            // Merged into the current listings, for the repositories added now.
            val added = repositoryUrls.value
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
        val text = fetchBytes(url, limits.indexBytes).bytes.decodeToString()
        val index = try {
            MangayomiIndex.parse(text)
        } catch (e: MangayomiIndexException) {
            throw JsSourceStoreException(Reason.INVALID_INDEX, "$url is no Mangayomi index: ${e.message}", e)
        }
        val info = try {
            MangayomiRepoInfo.parse(fetchBytes(MangayomiRepoInfo.infoUrl(url), limits.repoInfoBytes).bytes.decodeToString())
        } catch (e: JsSourceStoreException) {
            null
        }
        return Listing(info = info, entries = index.entries.map { JsSourceOffer(it, url) })
    }

    private fun offersOf(urls: Set<String>, listings: Map<String, Listing>): List<JsSourceOffer> =
        JsSourceOffer.merge(urls.sorted().mapNotNull { url -> listings[url]?.entries?.let { url to it.map(JsSourceOffer::descriptor) } })

    /** The best offer for [id] from the last reads, if any. */
    fun offer(id: Long): JsSourceOffer? = offersOf(repositoryUrls.value, listings.value).find { it.id == id }

    /** For installed sources whose version is still offered: the SHA-256 of the text the offer serves now. */
    private suspend fun checkInstalledTexts() {
        val checks = _installed.value.orEmpty().mapNotNull { source ->
            val offer = offer(source.id) ?: return@mapNotNull null
            if (MangayomiVersions.compare(source.descriptor.version, offer.descriptor.version) != 0) return@mapNotNull null
            source.id to offer.descriptor
        }
        val found = checks.mapNotNull { (id, descriptor) ->
            try {
                val text = fetchBytes(descriptor.sourceCodeUrl, limits.codeBytes).bytes.decodeToString()
                id to RemoteText(descriptor.sourceCodeUrl, descriptor.version, sha256Hex(text))
            } catch (e: JsSourceStoreException) {
                log("Could not check the text of JavaScript source ${descriptor.name}: ${e.message}")
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
        val requested = checkedUrl(offer.descriptor.sourceCodeUrl)
        val fetched = fetchBytes(offer.descriptor.sourceCodeUrl, limits.codeBytes)
        val text = fetched.bytes.decodeToString()
        val sha256 = sha256Hex(text)
        val installed = _installed.value.orEmpty().find { it.id == offer.id }
        if (installed != null) {
            remoteTexts.update { it + (offer.id to RemoteText(offer.descriptor.sourceCodeUrl, offer.descriptor.version, sha256)) }
        }
        return JsSourceDownload(
            offer = offer,
            host = fetched.url.host,
            sha256 = sha256,
            sizeBytes = text.encodeToByteArray().size.toLong(),
            installed = installed,
            requestedHost = requested.host,
        )
    }

    /** [download] of the best offer for the installed source [id] (an update, or the same version's new text). */
    suspend fun downloadUpdate(id: Long): JsSourceDownload =
        download(offer(id) ?: throw JsSourceStoreException(Reason.NOT_FOUND, "No repository offers the JavaScript source $id"))

    /**
     * Installs (or updates to) the text of [offer] if it still has [confirmedSha256]: downloads it, checks the hash,
     * loads it (reads its metadata, which runs the script) while the installed version stays in use, stores the text
     * and manifest, then swaps the new source in and stores its icon. Any failure before the manifest is written (a
     * text that does not load, a file that cannot be written) changes nothing: the installed version stays in use and
     * the new files are removed.
     *
     * @throws JsSourceStoreException
     */
    suspend fun install(offer: JsSourceOffer, confirmedSha256: String): IosJsSource {
        val descriptor = offer.descriptor
        val text = fetchBytes(descriptor.sourceCodeUrl, limits.codeBytes).bytes.decodeToString()
        val sha256 = sha256Hex(text)
        if (!sha256.equals(confirmedSha256.trim(), ignoreCase = true)) {
            throw JsSourceStoreException(
                Reason.SHA_MISMATCH,
                "The text of ${descriptor.name} changed since it was confirmed (SHA-256 $sha256, confirmed $confirmedSha256)",
            )
        }
        awaitLoaded()
        return mutex.withLock {
            val extension = runtime.extension(descriptor, text)
            val metadata = try {
                extension.metadata()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw JsSourceStoreException(Reason.LOAD_FAILED, "${descriptor.name} does not load: ${e.message}", e)
            }
            val icon = fetchIcon(descriptor.id, descriptor.iconUrl)
            val current = _installed.value.orEmpty()
            val previous = current.find { it.id == descriptor.id }
            val code = codePath(sha256)
            val codeWasThere = StoreFiles.isFile(code)
            val now = clock()
            val record = InstalledJsSource(
                descriptor = descriptor,
                repoUrl = offer.repoUrl,
                sha256 = sha256,
                installedAt = previous?.installedAt ?: now,
                updatedAt = now,
                metadata = metadata,
            )
            val updated = current.filterNot { it.id == descriptor.id } + record
            try {
                storage("store the text of ${descriptor.name}") {
                    withContext(Dispatchers.IO) { StoreFiles.writeAtomically(code, text.encodeToByteArray()) }
                }
                storage("store the JavaScript source manifest") { writeManifest(updated) }
            } catch (e: Throwable) {
                if (!codeWasThere) withContext(NonCancellable + Dispatchers.IO) { StoreFiles.delete(code) }
                throw e
            }
            // Written: from here on the new version is the installed one, also if the caller is cancelled.
            val source = IosJsSource(record, extension)
            withContext(NonCancellable) {
                _installed.value = updated
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
    suspend fun update(id: Long, confirmedSha256: String): IosJsSource =
        install(offer(id) ?: throw JsSourceStoreException(Reason.NOT_FOUND, "No repository offers the JavaScript source $id"), confirmedSha256)

    /**
     * Removes the installed source [id]: its source, text (unless another entry uses it), icon and preferences
     * ([IosJsSourceRuntime.clearPreferences]). Nothing is removed when the manifest cannot be written.
     *
     * @throws JsSourceStoreException
     */
    suspend fun uninstall(id: Long) {
        awaitLoaded()
        mutex.withLock {
            val current = _installed.value.orEmpty()
            val record = current.find { it.id == id } ?: return
            val updated = current - record
            storage("store the JavaScript source manifest") { writeManifest(updated) }
            withContext(NonCancellable) {
                _installed.value = updated
                loaded.update { it - id }
                loadErrors.update { it - id }
                remoteTexts.update { it - id }
                runtime.clearPreferences(record.descriptor)
                withContext(Dispatchers.IO) {
                    deleteUnusedCode(record.sha256, updated)
                    StoreFiles.delete(iconPath(id))
                }
            }
        }
    }

    /**
     * Reads the metadata of the installed source [id] again (runs its script) and caches it, e.g. after one of its
     * preferences changed (headers and `supportsLatest` may depend on them).
     *
     * @throws JsSourceStoreException
     */
    suspend fun refreshMetadata(id: Long): JsSourceMetadata {
        awaitLoaded()
        return mutex.withLock {
            val source = loaded.value[id] ?: throw JsSourceStoreException(Reason.NOT_FOUND, "The JavaScript source $id is not loaded")
            val metadata = try {
                source.extension.metadata()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw JsSourceStoreException(Reason.LOAD_FAILED, "${source.descriptor.name} does not load: ${e.message}", e)
            }
            if (metadata != source.metadata) {
                val record = source.record.copy(metadata = metadata)
                val updated = _installed.value.orEmpty().map { if (it.id == id) record else it }
                storage("store the JavaScript source manifest") { writeManifest(updated) }
                _installed.value = updated
                loaded.update { it + (id to IosJsSource(record, source.extension)) }
            }
            metadata
        }
    }

    // ---- Lookups ----

    /** The loaded source with the Komikku source id [sourceId], whether or not NSFW sources are shown. */
    fun source(sourceId: Long): IosJsSource? = loaded.value.values.find { it.sourceId == sourceId }

    /** The installed record of the Komikku source [sourceId], if it is an installed JavaScript source. */
    fun installedSource(sourceId: Long): InstalledJsSource? = _installed.value?.find { it.sourceId == sourceId }

    /** The path of the stored icon of the Komikku source [sourceId], if it is installed and has one. */
    fun iconFileForSource(sourceId: Long): String? =
        installedSource(sourceId)?.let { iconPath(it.id) }?.takeIf { StoreFiles.isFile(it) }

    /** Stops background work and closes [runtime] (and the HTTP client the store created). */
    fun close() {
        scope.cancel()
        runtime.close()
        ownedHttp?.close()
    }

    // ---- Files ----

    /** Waits for [load] (outside the lock, which [load] holds). */
    private suspend fun awaitLoaded() {
        if (!_isLoaded.value) _isLoaded.first { it }
    }

    /**
     * Reads the manifest and builds every installed source, under the store's lock: an install, update or uninstall
     * started meanwhile waits, so it can neither be overwritten by the start-up state nor race it.
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
        val sources = mutableMapOf<Long, IosJsSource>()
        val errors = mutableMapOf<Long, String>()
        var metadataCached = false
        val updated = records.map { record ->
            try {
                if (!SHA256.matches(record.sha256)) throw IOException("The SHA-256 of ${record.descriptor.name} is malformed")
                val text = withContext(Dispatchers.IO) { StoreFiles.readBytes(codePath(record.sha256)) }?.decodeToString()
                    ?: throw IOException("The text file of ${record.descriptor.name} is missing")
                if (!sha256Hex(text).equals(record.sha256, ignoreCase = true)) {
                    throw IOException("The text file of ${record.descriptor.name} does not match its SHA-256")
                }
                val extension = runtime.extension(record.descriptor, text)
                // The cached metadata spares running the script at start-up; a record without it (none could be read)
                // gets it now.
                val withMetadata = if (record.metadata != null) {
                    record
                } else {
                    metadataCached = true
                    record.copy(metadata = extension.metadata())
                }
                sources[record.id] = IosJsSource(withMetadata, extension)
                withMetadata
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log("Could not load the JavaScript source ${record.descriptor.name} (${record.id}): ${e.message}")
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
                log("Could not cache the JavaScript source metadata: ${e.message}")
            }
        }
    }

    private fun readManifest(): List<InstalledJsSource> {
        if (!StoreFiles.exists(manifestPath)) return emptyList()
        return try {
            val bytes = StoreFiles.readBytes(manifestPath) ?: throw IOException("$manifestPath cannot be read")
            val manifest = json.decodeFromString(JsSourceManifest.serializer(), bytes.decodeToString())
            manifest.sources.distinctBy { it.id }
        } catch (e: Exception) {
            val aside = "$manifestPath.corrupt-${clock()}"
            val kept = StoreFiles.move(manifestPath, aside)
            log("The JavaScript source manifest could not be read; starting empty (kept as ${aside.substringAfterLast('/')}: $kept): ${e.message}")
            emptyList()
        }
    }

    private suspend fun writeManifest(sources: List<InstalledJsSource>) = withContext(Dispatchers.IO) {
        val text = json.encodeToString(JsSourceManifest.serializer(), JsSourceManifest(sources = sources.sortedBy { it.id }))
        StoreFiles.writeAtomically(manifestPath, text.encodeToByteArray())
    }

    private fun codePath(sha256: String): String = "$codeDir/${sha256.lowercase()}.js"

    private fun iconPath(id: Long): String = "$iconDir/$id"

    private fun deleteUnusedCode(sha256: String, installed: List<InstalledJsSource>) {
        if (installed.none { it.sha256.equals(sha256, ignoreCase = true) }) StoreFiles.delete(codePath(sha256))
    }

    /** Best effort: a missing, insecure or too large icon only means no icon (`null`). */
    private suspend fun fetchIcon(id: Long, iconUrl: String): ByteArray? {
        if (iconUrl.isBlank()) return null
        return try {
            fetchBytes(iconUrl, limits.iconBytes).bytes
        } catch (e: JsSourceStoreException) {
            log("No icon for JavaScript source $id: ${e.message}")
            null
        }
    }

    /** Stores [bytes] as the icon of [id]; without an icon (or when it cannot be written) an older one is removed. */
    private fun storeIcon(id: Long, bytes: ByteArray?) {
        val path = iconPath(id)
        if (bytes != null) {
            try {
                StoreFiles.writeAtomically(path, bytes)
                return
            } catch (e: IOException) {
                log("Could not store the icon of JavaScript source $id: ${e.message}")
            }
        }
        StoreFiles.delete(path)
    }

    /** Runs the file operation [block], reporting a file failure as [Reason.STORAGE_FAILED]. */
    private inline fun <T> storage(what: String, block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw JsSourceStoreException(Reason.STORAGE_FAILED, "Could not $what: ${e.message}", e)
    }

    // ---- HTTP ----

    private fun checkedUrl(url: String): Url {
        val parsed = try {
            Url(DarwinHostHttp.httpUrl(url.trim()))
        } catch (e: Exception) {
            null
        }
        if (parsed == null || !isAllowed(parsed)) {
            throw JsSourceStoreException(Reason.INSECURE_URL, "Only https URLs are allowed: $url")
        }
        return parsed
    }

    private fun isAllowed(url: Url): Boolean = url.protocol.name in HTTP_SCHEMES && url.host.isNotEmpty() && allowUrl(url)

    /** A downloaded file and the [url] that served it (after redirects). */
    private class Fetched(val url: Url, val bytes: ByteArray)

    /**
     * GET [url] into memory, at most [limit] bytes (the client is asked to stop reading above it, and the body is
     * checked again). Redirects are followed here, at most [MAX_REDIRECTS], each target checked by [allowUrl] before
     * it is requested.
     */
    private suspend fun fetchBytes(url: String, limit: Long): Fetched {
        var target = checkedUrl(url)
        repeat(MAX_REDIRECTS + 1) {
            val request = HostHttpRequest(
                method = "GET",
                url = target.toString(),
                headers = listOf("Cache-Control" to "no-cache"),
                body = null,
                timeoutMillis = limits.timeoutMillis,
                followRedirects = false,
                maxBodyBytes = limit,
            )
            val response = try {
                http.execute(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.message == HttpBridge.tooLargeMessage(limit)) {
                    throw JsSourceStoreException(Reason.TOO_LARGE, "$url is larger than $limit bytes", e)
                }
                throw JsSourceStoreException(Reason.DOWNLOAD_FAILED, "Could not download $url: ${e.message}", e)
            }
            if (response.statusCode in REDIRECT_CODES) {
                val location = response.headers.firstOrNull { it.first.equals("Location", ignoreCase = true) }?.second
                    ?.let { DarwinHostHttp.resolve(target, it) }
                    ?: throw JsSourceStoreException(Reason.DOWNLOAD_FAILED, "Could not download $url: bad redirect")
                if (!isAllowed(location)) {
                    throw JsSourceStoreException(Reason.INSECURE_URL, "$url redirected to $location, which is not https")
                }
                target = location
                return@repeat
            }
            if (response.statusCode !in 200..299) {
                throw JsSourceStoreException(Reason.DOWNLOAD_FAILED, "Could not download $url: HTTP ${response.statusCode}")
            }
            if (response.body.size > limit) throw JsSourceStoreException(Reason.TOO_LARGE, "$url is larger than $limit bytes")
            return Fetched(target, response.body)
        }
        throw JsSourceStoreException(Reason.DOWNLOAD_FAILED, "Could not download $url: more than $MAX_REDIRECTS redirects")
    }

    companion object {
        /** The store's directory under Application Support (the JVM store's directory name). */
        const val DIRECTORY = "mangayomi-js"
        const val MANIFEST = "manifest.json"

        /** The repository list's key in the defaults (the JVM preference key). */
        const val REPOSITORIES_KEY = "mangayomi_js_repos"

        /** Redirects followed by one download. */
        const val MAX_REDIRECTS = 5

        private val REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)

        private val HTTP_SCHEMES = setOf("http", "https")

        private val SHA256 = Regex("[0-9a-fA-F]{64}")
    }
}
