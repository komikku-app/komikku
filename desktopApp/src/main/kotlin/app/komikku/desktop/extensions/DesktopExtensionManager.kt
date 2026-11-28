package app.komikku.desktop.extensions

import android.content.Context
import android.widget.Toast
import app.komikku.desktop.compat.UnsupportedOnDesktop
import app.komikku.desktop.extension.ExtensionPackageResult
import app.komikku.desktop.extension.ExtensionPaths
import app.komikku.desktop.extension.ExtensionRuntime
import app.komikku.shared.extension.PlatformExtensionManager
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.AvailableExtensionVersionComparator
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.model.findAvailable
import eu.kanade.tachiyomi.extension.model.identityKey
import eu.kanade.tachiyomi.extension.model.isOutdatedComparedTo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.system.toast
import exh.source.BlacklistedSources
import exh.source.ExhPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.extension.model.ChangedExtensionStore
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.model.KOMIKKU_SIGNATURE
import mihon.domain.extension.model.label
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop extension manager: the counterpart of Android's `ExtensionManager` + `ExtensionLoader` +
 * `ExtensionInstaller`, on top of the desktop extension runtime (`:desktop:extension-runtime`).
 *
 * Installed extensions are the APK files in `<extensionsDir>/apks/<package>.apk` (desktop has no
 * package manager; this plays the role of Android's private extensions). Loading follows
 * `ExtensionLoader`: extension checks and signatures ([ExtensionRuntime.inspect]), trust (same rules
 * as `TrustExtension`: Komikku's key, a saved store's signing key, or a trusted
 * `package:versionCode:signature` entry in `SourcePreferences.trustedExtensions`), NSFW policy, then
 * dex → jar conversion, class loading and source instantiation ([ExtensionRuntime.load]).
 * Availability comes from the saved extension stores through [ExtensionStoreRepository] (same index
 * formats, disabled stores skipped). Installing downloads the APK with [NetworkHelper.client], then
 * applies `ExtensionLoader.installPrivateExtensionFile`'s rules (no downgrade, same signer as the
 * installed version).
 */
class DesktopExtensionManager(
    private val context: Context,
    extensionsDir: File,
    private val runtime: ExtensionRuntime,
    private val network: NetworkHelper,
    private val storeRepository: ExtensionStoreRepository,
    private val preferences: SourcePreferences,
    private val exhPreferences: ExhPreferences,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : PlatformExtensionManager {

    private val apkDir = File(extensionsDir, "apks")
    private val downloadDir = File(extensionsDir, "downloads")

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val installedMap = MutableStateFlow(emptyMap<String, Extension.Installed>())
    private val availableMap = MutableStateFlow(emptyMap<String, Extension.Available>())
    private val untrustedMap = MutableStateFlow(emptyMap<String, Extension.Untrusted>())

    private val installedList = MutableStateFlow(emptyList<Extension.Installed>())
    override val installedExtensionsFlow: StateFlow<List<Extension.Installed>> = installedList.asStateFlow()

    private val availableList = MutableStateFlow(emptyList<Extension.Available>())
    override val availableExtensionsFlow: StateFlow<List<Extension.Available>> = availableList.asStateFlow()

    private val untrustedList = MutableStateFlow(emptyList<Extension.Untrusted>())
    override val untrustedExtensionsFlow: StateFlow<List<Extension.Untrusted>> = untrustedList.asStateFlow()

    /** Serializes installs, uninstalls, trust changes and the initial load (they all touch [apkDir]). */
    private val mutex = Mutex()

    /** Guards the read-modify-write of the installed / untrusted entries ([updateInstalled], [updateUntrusted]). */
    private val entriesLock = Any()

    private val installJobs = ConcurrentHashMap<String, Job>()

    private var availableSourcesData: Map<Long, StubSource> = emptyMap()

    /**
     * The extensions of the last successful store refresh ([findAvailableExtensions]), `null` before the first: the
     * initial load sets the statuses of the entries it loads from them, so a refresh that ended before it is not lost
     * (P23 fixes review). Written before the refresh updates the entries, read under [entriesLock].
     */
    @Volatile
    private var lastAvailableExtensions: List<Extension.Available>? = null

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages().isSet()

    /**
     * APK hash per installed package, to prune the converted-jar cache. Declared before `init`: the initial load
     * launched there can run on another thread before the rest of the constructor (a null map otherwise).
     */
    private val loadedApkHashes = ConcurrentHashMap<String, String>()

    /** Why the last install or load of a package failed (one line), shown by the Extensions tab ([installError]). */
    private val installErrors = ConcurrentHashMap<String, String>()

    /**
     * The class loader of each loaded package. Closed ([closeLoader]) once the manager no longer lists its sources: an
     * open loader keeps its converted jar open, which Windows then refuses to delete (old jars piled up on updates).
     */
    private val classLoaders = ConcurrentHashMap<String, Closeable>()

    /** Set by [close]: a loader created afterwards is closed at once. */
    @Volatile
    private var closed = false

    private val unsupportedList = MutableStateFlow(emptyList<UnsupportedExtension>())

    /**
     * Installed extensions whose code needs a compat member that cannot work on desktop ([UnsupportedOnDesktop]):
     * not loaded, listed by the Extensions tab as "unsupported on desktop: needs …" until uninstalled or updated.
     */
    val unsupportedExtensionsFlow: StateFlow<List<UnsupportedExtension>> = unsupportedList.asStateFlow()

    init {
        scope.launch {
            mutex.withLock { initExtensions() }
            _isInitialized.value = true
        }
    }

    // ---- loading ---------------------------------------------------------------------------------

    private suspend fun initExtensions() {
        apkDir.mkdirs()
        val results = apkDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == APK_EXTENSION }
            .map { loadExtension(it) }
        val loaded = results.filterIsInstance<LoadResult.Success>().associate { it.extension.pkgName to it.extension }
        // P23 fixes review: with the statuses of a store refresh that may have ended before this load
        updateInstalled { _ -> lastAvailableExtensions?.let { statusesOf(loaded, it) } ?: loaded }
        setUntrusted(
            results.filterIsInstance<LoadResult.Untrusted>()
                .associate { it.extension.pkgName to it.extension }
                .filterNot { (_, extension) -> extension.isBlacklisted() },
        )
        runtime.pruneJarCache(loadedApkHashes.values.toSet())
    }

    private suspend fun loadExtension(apk: File): LoadResult {
        val extension = when (val result = runtime.inspect(apk)) {
            is ExtensionPackageResult.Rejected -> {
                logcat(LogPriority.WARN) { "Extension ${apk.name} not loaded: ${result.reason}" }
                failed(apk.nameWithoutExtension, result.reason)
                return LoadResult.Error
            }
            is ExtensionPackageResult.Valid -> result.extension
        }
        loadedApkHashes[extension.pkgName] = extension.apk.sha256
        setUnsupported(extension.pkgName, null)
        val stores = storeRepository.getAll()
        val storeName = stores.firstOrNull { store -> extension.signatures.all { it == store.signingKey } }?.label

        if (!isTrusted(extension.pkgName, extension.versionCode, extension.signatures, stores)) {
            logcat(LogPriority.WARN) { "Extension ${extension.pkgName} isn't trusted" }
            return LoadResult.Untrusted(
                Extension.Untrusted(
                    name = extension.name,
                    pkgName = extension.pkgName,
                    versionName = extension.versionName,
                    versionCode = extension.versionCode,
                    libVersion = extension.libVersion,
                    signatureHash = extension.signatureHash,
                    storeName = storeName,
                ),
            )
        }

        if (!preferences.showNsfwSource().get() && extension.isNsfw) {
            logcat(LogPriority.WARN) { "NSFW extension ${extension.pkgName} not allowed" }
            failed(extension.pkgName, "NSFW extension ${extension.pkgName} not allowed")
            return LoadResult.Error
        }

        val loaded = try {
            runtime.load(extension)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Extension load error: ${extension.name} (${extension.pkgName})" }
            val unsupported = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<UnsupportedOnDesktop>().firstOrNull()
            if (unsupported == null) {
                failed(extension.pkgName, e.message ?: e.toString())
            } else {
                failed(extension.pkgName, "Unsupported on desktop: needs ${unsupported.needs}")
                setUnsupported(
                    extension.pkgName,
                    UnsupportedExtension(extension.name, extension.pkgName, extension.versionName, unsupported.needs),
                )
            }
            return LoadResult.Error
        }

        installErrors.remove(extension.pkgName)
        (loaded.classLoader as? Closeable)?.let { loader ->
            classLoaders.put(extension.pkgName, loader)?.let { closeLoader(extension.pkgName, it) }
            if (closed) classLoaders.remove(extension.pkgName)?.let { closeLoader(extension.pkgName, it) }
        }
        val langs = loaded.sources.map { it.lang }.toSet()
        return LoadResult.Success(
            Extension.Installed(
                name = extension.name,
                pkgName = extension.pkgName,
                versionName = extension.versionName,
                versionCode = extension.versionCode,
                libVersion = extension.libVersion,
                lang = when (langs.size) {
                    0 -> ""
                    1 -> langs.first()
                    else -> "all"
                },
                isNsfw = extension.isNsfw,
                signatureHash = extension.signatureHash,
                storeName = storeName,
                pkgFactory = extension.pkgFactory,
                sources = loaded.sources,
                icon = extension.apk.icon?.let(::ExtensionIconDrawable),
                isShared = false,
            ),
        )
    }

    /**
     * Why the last install (or load) of [pkgName] failed: the first line of the cause, e.g. the loader's error. `null`
     * after a success or while an install of it runs.
     */
    fun installError(pkgName: String): String? = installErrors[pkgName]

    private fun failed(pkgName: String, reason: String) {
        installErrors[pkgName] = reason.lineSequence().first()
    }

    /** `TrustExtension.isTrusted` without the debug-build shortcut. */
    private fun isTrusted(pkgName: String, versionCode: Long, fingerprints: List<String>, stores: List<ExtensionStore>): Boolean {
        if (KOMIKKU_SIGNATURE in fingerprints) return true
        val trustedFingerprints = stores.map { it.signingKey }.toHashSet()
        val key = "$pkgName:$versionCode:${fingerprints.last()}"
        return trustedFingerprints.any { it in fingerprints } || key in preferences.trustedExtensions().get()
    }

    // ---- queries -----------------------------------------------------------------------------------

    override fun getExtensionPackage(sourceId: Long): String? =
        installedExtensionsFlow.value.find { extension -> extension.sources.any { it.id == sourceId } }?.pkgName

    override fun getSourceData(id: Long): StubSource? = availableSourcesData[id]

    // ---- available extensions ------------------------------------------------------------------------

    override suspend fun findAvailableExtensions() {
        val fetched = try {
            withContext(Dispatchers.IO) { storeRepository.fetchExtensions(preferences.disabledRepos().get()) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            context.toast(MR.strings.extension_api_error)
            return
        }
        warnAboutChangedStores(fetched.changedStores)
        val extensions = fetched.extensions

        enableAdditionalSubLanguages(extensions)
        lastAvailableExtensions = extensions

        // Several stores can list the same package signed by the same key: keep the newest one.
        setAvailable(
            extensions.groupBy { it.identityKey }
                .mapValues { (_, same) -> same.maxWith(AvailableExtensionVersionComparator) },
        )
        updatedInstalledExtensionsStatuses(extensions)
        if (extensions.isNotEmpty()) {
            availableSourcesData = extensions
                .flatMap { ext -> ext.sources.map { it.toStubSource() } }
                .associateBy { it.id }
        }
    }

    private fun warnAboutChangedStores(stores: List<ChangedExtensionStore>) {
        stores.distinctBy { it.store.indexUrl }.forEach {
            context.toast(
                context.stringResource(KMR.strings.ext_store_signing_key_changed, it.store.name, it.servedName),
                Toast.LENGTH_LONG,
            )
        }
    }

    /** Same as Android: on first run, also enable the device language's sub-languages (e.g. zh-Hans). */
    private fun enableAdditionalSubLanguages(extensions: List<Extension.Available>) {
        if (subLanguagesEnabledOnFirstRun || extensions.isEmpty()) return
        val deviceLanguage = Locale.getDefault().language
        val languagesToEnable = extensions
            .flatMap(Extension.Available::sources)
            .map(Extension.Available.Source::lang)
            .distinct()
            .filter { it != deviceLanguage && it.startsWith(deviceLanguage) }
        preferences.enabledLanguages().set(preferences.enabledLanguages().defaultValue() + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    private fun updatedInstalledExtensionsStatuses(availableExtensions: List<Extension.Available>) {
        // P23 review fix: computed from the current entries in one atomic step, so an install / update / uninstall
        // (under the mutex) that lands meanwhile is not overwritten by a stale snapshot
        updateInstalled { current -> statusesOf(current, availableExtensions) }
    }

    private fun statusesOf(
        current: Map<String, Extension.Installed>,
        availableExtensions: List<Extension.Available>,
    ): Map<String, Extension.Installed> {
        val installed = current.toMutableMap()
        var changed = false
        for ((pkgName, extension) in current) {
            val availableExt = extension.findAvailable(availableExtensions)
            if (availableExt == null && (!extension.isObsolete || extension.hasUpdate)) {
                installed[pkgName] = extension.copy(isObsolete = true, hasUpdate = false)
                changed = true
            } else if (extension.isBlacklisted() && !extension.isRedundant) {
                installed[pkgName] = extension.copy(isRedundant = true)
                changed = true
            } else if (availableExt != null) {
                installed[pkgName] = extension.copy(
                    hasUpdate = extension.isOutdatedComparedTo(availableExt),
                    store = availableExt.store,
                    isObsolete = false,
                    storeName = extension.storeName ?: availableExt.store.label,
                )
                changed = true
            }
        }
        return if (changed) installed else current
    }

    // ---- install / update / uninstall ----------------------------------------------------------------

    override fun installExtension(extension: Extension.Available): Flow<InstallStep> {
        // The extensions list splits multi-source extensions per source (`<package>-<sourceId>`); install
        // the extension the entry came from.
        val target = availableMap.value.values.firstOrNull {
            it.apkUrl == extension.apkUrl && it.signatureHash == extension.signatureHash
        } ?: extension
        val key = installKey(target)
        return flow {
            installJobs[key] = currentCoroutineContext().job
            installErrors.remove(target.pkgName)
            emit(InstallStep.Pending)
            // A store's index is third-party data too: its package name names the download
            require(ExtensionPaths.isValidPackageName(target.pkgName)) { "'${target.pkgName}' is not a valid package name" }
            emit(InstallStep.Downloading)
            downloadDir.mkdirs()
            val download = File.createTempFile("${target.pkgName}-", ".apk", downloadDir)
            try {
                network.client.newCall(GET(target.apkUrl)).awaitSuccess().use { response ->
                    download.outputStream().use { output -> response.body.byteStream().copyTo(output) }
                }
                emit(InstallStep.Installing)
                emit(if (installApkFile(download, target.pkgName)) InstallStep.Installed else InstallStep.Error)
            } finally {
                download.delete()
            }
        }
            .flowOn(Dispatchers.IO)
            .onCompletion { cause ->
                installJobs.remove(key)
                if (cause != null && cause !is kotlinx.coroutines.CancellationException) {
                    logcat(LogPriority.ERROR, cause) { "Failed to install ${target.pkgName}" }
                    failed(target.pkgName, cause.toString())
                }
            }
            .catchToError()
    }

    private fun Flow<InstallStep>.catchToError(): Flow<InstallStep> = flow {
        try {
            collect { emit(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emit(InstallStep.Error)
        }
    }

    override fun updateExtension(extension: Extension.Installed): Flow<InstallStep> {
        val available = availableMap.value[extension.identityKey] ?: return emptyFlow()
        return installExtension(available)
    }

    override fun cancelInstallUpdateExtension(extension: Extension) {
        installJobs.entries.filter { it.key.startsWith("${extension.pkgName}_") || it.key == installKey(extension) }
            .forEach { it.value.cancel() }
    }

    private fun installKey(extension: Extension) = "${extension.pkgName}_${extension.signatureHash}"

    /**
     * Installs the APK [file] (the rules of `ExtensionLoader.installPrivateExtensionFile`: must be an
     * extension, no downgrade, same signer as the installed version), then loads and registers it.
     */
    suspend fun installApkFile(file: File, expectedPkgName: String? = null): Boolean = mutex.withLock {
        val inspected = runtime.inspect(file)
        if (inspected !is ExtensionPackageResult.Valid) {
            val reason = (inspected as ExtensionPackageResult.Rejected).reason
            logcat(LogPriority.ERROR) { "Not installing ${file.name}: $reason" }
            failed(expectedPkgName ?: file.nameWithoutExtension, reason)
            return@withLock false
        }
        val extension = inspected.extension
        if (expectedPkgName != null && extension.pkgName != expectedPkgName) {
            logcat(LogPriority.ERROR) { "Downloaded package ${extension.pkgName} is not $expectedPkgName" }
            failed(expectedPkgName, "Downloaded package ${extension.pkgName} is not $expectedPkgName")
            return@withLock false
        }
        apkDir.mkdirs()
        val target = ExtensionPaths.packageFileOrNull(apkDir, extension.pkgName, APK_SUFFIX)
        val temp = ExtensionPaths.packageFileOrNull(apkDir, extension.pkgName, "$APK_SUFFIX.tmp")
        if (target == null || temp == null) {
            logcat(LogPriority.ERROR) { "Not installing ${extension.pkgName}: its file would be outside $apkDir" }
            failed(extension.pkgName, "Not installing ${extension.pkgName}: its file would be outside the extensions folder")
            return@withLock false
        }
        val current = if (target.isFile) runtime.inspect(target) as? ExtensionPackageResult.Valid else null
        if (current != null) {
            if (extension.versionCode < current.extension.versionCode) {
                logcat(LogPriority.ERROR) { "Installed extension version is higher. Downgrading is not allowed." }
                failed(extension.pkgName, "Installed extension version is higher. Downgrading is not allowed.")
                return@withLock false
            }
            if (!extension.signatures.containsAll(current.extension.signatures)) {
                logcat(LogPriority.ERROR) { "Installed extension signature is not matched." }
                failed(extension.pkgName, "Installed extension signature is not matched.")
                return@withLock false
            }
        }

        file.copyTo(temp, overwrite = true)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                failed(extension.pkgName, "Could not write $target")
                return@withLock false
            }
        }

        // P22 review: the new version replaces the old entry in one emission of each list. Removing first and adding
        // after gave observers a list without the extension (an open extension info took it as uninstalled and closed,
        // the source manager dropped and re-added its sources).
        val pkgName = extension.pkgName
        // The old version's loader is closed and its jar deleted once the new entry replaced it
        val previous = classLoaders.remove(pkgName)
        val result = loadExtension(target)
        when (result) {
            is LoadResult.Success -> replace(pkgName, installed = result.extension.withUpdateCheck())
            is LoadResult.Untrusted ->
                replace(pkgName, untrusted = result.extension.takeUnless { it.isBlacklisted() })
            LoadResult.Error -> replace(pkgName)
        }
        previous?.let { closeLoader(pkgName, it) }
        runtime.pruneJarCache(loadedApkHashes.values.toSet())
        result !is LoadResult.Error
    }

    /**
     * Sets [pkgName]'s entries at once: [installed] (unless blacklisted) or [untrusted], the other list without it; an
     * entry already listed keeps its position. `null` for both removes it from both.
     */
    private fun replace(
        pkgName: String,
        installed: Extension.Installed? = null,
        untrusted: Extension.Untrusted? = null,
    ) {
        val listed = installed?.takeUnless { extension ->
            extension.isBlacklisted().also { blacklisted ->
                if (blacklisted) {
                    logcat { "Removing blacklisted extension: (name: ${extension.name}, pkgName: ${extension.pkgName})" }
                }
            }
        }
        updateInstalled { if (listed != null) it + (pkgName to listed) else it - pkgName }
        updateUntrusted { if (untrusted != null) it + (pkgName to untrusted) else it - pkgName }
    }

    override fun uninstallExtension(extension: Extension) {
        scope.launch { uninstall(extension.pkgName) }
    }

    /** [uninstallExtension] that returns when the extension is gone. */
    suspend fun uninstall(pkgName: String) = mutex.withLock {
        ExtensionPaths.packageFileOrNull(apkDir, pkgName, APK_SUFFIX)?.delete()
        // Like Android, uninstalling deletes the extension's data
        ExtensionPaths.packageFileOrNull(runtime.dataDir, pkgName)?.let { folder ->
            try {
                deleteTree(folder.toPath())
            } catch (e: IOException) {
                logcat(LogPriority.WARN, e) { "Could not delete $folder" }
            }
        }
        loadedApkHashes.remove(pkgName)
        setUnsupported(pkgName, null)
        unregister(pkgName)
        classLoaders.remove(pkgName)?.let { closeLoader(pkgName, it) }
        runtime.pruneJarCache(loadedApkHashes.values.toSet())
    }

    /**
     * Closes the class loaders of the loaded extensions (app exit, test teardown), so their jars can be deleted; their
     * sources can no longer load classes they have not used yet. Extensions loaded afterwards are closed at once.
     */
    fun close() {
        closed = true
        classLoaders.keys.toList().forEach { pkgName -> classLoaders.remove(pkgName)?.let { closeLoader(pkgName, it) } }
    }

    private fun closeLoader(pkgName: String, loader: Closeable) {
        try {
            loader.close()
        } catch (e: IOException) {
            logcat(LogPriority.WARN, e) { "Could not close the class loader of $pkgName" }
        }
    }

    // ---- trust -------------------------------------------------------------------------------------

    override suspend fun trust(extension: Extension.Untrusted) {
        mutex.withLock {
            untrustedMap.value[extension.pkgName] ?: return
            preferences.trustedExtensions().getAndSet { trusted ->
                trusted.filterNot { it.startsWith("${extension.pkgName}:") }.toMutableSet()
                    .also { it += "${extension.pkgName}:${extension.versionCode}:${extension.signatureHash}" }
            }
            updateUntrusted { it - extension.pkgName }
            val apk = ExtensionPaths.packageFileOrNull(apkDir, extension.pkgName, APK_SUFFIX) ?: return
            (loadExtension(apk) as? LoadResult.Success)?.let { register(it.extension.withUpdateCheck()) }
        }
    }

    // ---- registration ----------------------------------------------------------------------------

    private fun register(extension: Extension.Installed) {
        if (extension.isBlacklisted()) {
            logcat { "Removing blacklisted extension: (name: ${extension.name}, pkgName: ${extension.pkgName})" }
            return
        }
        updateInstalled { it + (extension.pkgName to extension) }
    }

    private fun unregister(pkgName: String) {
        updateInstalled { it - pkgName }
        updateUntrusted { it - pkgName }
    }

    private fun Extension.Installed.withUpdateCheck(): Extension.Installed {
        val available = availableMap.value[identityKey] ?: return this
        return if (isOutdatedComparedTo(available)) copy(hasUpdate = true) else this
    }

    /** SY: the E-Hentai extension is replaced by the built-in source when the hentai features are on. */
    private fun Extension.isBlacklisted(): Boolean =
        pkgName in BlacklistedSources.BLACKLISTED_EXTENSIONS &&
            preferences.enableSourceBlacklist().get() &&
            exhPreferences.isHentaiEnabled().get()

    private fun setUntrusted(map: Map<String, Extension.Untrusted>) = updateUntrusted { map }

    /**
     * P23 review fix: [transform] the current installed entries and publish the result (map and list) in one step, so
     * a write computed from an older snapshot cannot overwrite a newer one (the status refresh runs outside [mutex]).
     * The pending updates count is set from the result in the same step (P23 fixes review: set after the lock, a
     * refresh could write the count of an older snapshot after an install).
     */
    private fun updateInstalled(transform: (Map<String, Extension.Installed>) -> Map<String, Extension.Installed>) {
        synchronized(entriesLock) {
            val current = installedMap.value
            val next = transform(current)
            if (next != current) {
                installedMap.value = next
                installedList.value = next.values.toList()
            }
            preferences.extensionUpdatesCount().set(next.values.count { it.hasUpdate })
        }
    }

    private fun updateUntrusted(transform: (Map<String, Extension.Untrusted>) -> Map<String, Extension.Untrusted>) {
        synchronized(entriesLock) {
            val current = untrustedMap.value
            val next = transform(current)
            if (next != current) {
                untrustedMap.value = next
                untrustedList.value = next.values.toList()
            }
        }
    }

    /** Lists [pkgName] as [unsupported], or not when `null`. */
    private fun setUnsupported(pkgName: String, unsupported: UnsupportedExtension?) {
        unsupportedList.update { list ->
            (list.filterNot { it.pkgName == pkgName } + listOfNotNull(unsupported)).sortedBy { it.name.lowercase() }
        }
    }

    private fun setAvailable(map: Map<String, Extension.Available>) {
        availableMap.value = map
        val blacklistEnabled = preferences.enableSourceBlacklist().get()
        availableList.update { map.values.filterNot { blacklistEnabled && it.isBlacklisted() } }
    }

    companion object {
        private const val APK_EXTENSION = "apk"
        private const val APK_SUFFIX = ".$APK_EXTENSION"
    }
}

/** An installed extension that [DesktopExtensionManager] does not load: its code needs [needs], which desktop lacks. */
data class UnsupportedExtension(val name: String, val pkgName: String, val versionName: String, val needs: String)

/** Deletes [root] and what is under it without following symbolic links (an extension's links point anywhere). */
@Throws(IOException::class)
private fun deleteTree(root: Path) {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(
        root,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        },
    )
}
