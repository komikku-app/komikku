package app.komikku.desktop.source

import android.content.Context
import app.komikku.shared.extension.PlatformExtensionManager
import app.komikku.sourcejs.mihon.MangayomiJsSource
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.log.xLogE
import exh.source.BlacklistedSources
import exh.source.ExhPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.LocalSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop [SourceManager], modelled on `AndroidSourceManager`: the local source plus the sources of
 * the installed extensions ([extensionManager]), rebuilt whenever they change; the E-Hentai extension
 * sources are dropped while the hentai features are on (SY blacklist). Every loaded source is
 * recorded as a stub source in the database, and unknown ids resolve to database stubs, then to the
 * available extensions' metadata.
 *
 * Installed Mangayomi JavaScript sources ([jsSources], from the JavaScript source store) are added after the
 * extensions' sources; their stubs are named [MangayomiJsSource.stubName]. The manager is initialized only once
 * [jsSources] gave its first list (`MangayomiSourceStore.sourcesOrEmpty` gives it once the store loaded its sources,
 * at most after its timeout), so an installed JavaScript source is not taken for a missing one at start-up.
 *
 * Extension sources with an SY delegate are wrapped like Android's (`DesktopDelegatedSources`: Pururin, MangaDex, 8Muses,
 * NHentai, LANraragi). Not on desktop (yet): SY's built-in E-Hentai / merged sources.
 */
class DesktopSourceManager(
    private val context: Context,
    fileSystem: LocalSourceFileSystem,
    coverManager: LocalCoverManager,
    private val stubSourceRepository: StubSourceRepository,
    allowHiddenFiles: () -> Boolean,
    private val extensionManager: PlatformExtensionManager? = null,
    exhPreferences: ExhPreferences? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    jsSources: Flow<List<Source>>? = null,
) : SourceManager {

    private val localSource = LocalSource(context, fileSystem, coverManager, allowHiddenFiles)

    private val sourcesMap = MutableStateFlow<Map<Long, Source>>(mapOf(LocalSource.ID to localSource))

    private val stubSources = ConcurrentHashMap<Long, StubSource>()

    private val _isInitialized = MutableStateFlow(extensionManager == null && jsSources == null)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    override val sources: Flow<List<Source>> = sourcesMap.map { it.values.toList() }

    init {
        if (extensionManager != null || jsSources != null) {
            scope.launch {
                combine(
                    extensionManager?.installedExtensionsFlow ?: flowOf(emptyList()),
                    extensionManager?.isInitialized ?: flowOf(true),
                    exhPreferences?.isHentaiEnabled()?.changes() ?: flowOf(false),
                    jsSources ?: flowOf(emptyList()),
                ) { extensions, initialized, isHentaiEnabled, js -> Loaded(extensions, initialized, isHentaiEnabled, js) }
                    .collectLatest { (extensions, initialized, isHentaiEnabled, js) ->
                        val map = linkedMapOf<Long, Source>(LocalSource.ID to localSource)
                        extensions.forEach { extension ->
                            val sources = guarded("the sources of ${extension.pkgName}") { extension.sources } ?: return@forEach
                            sources.forEach { source ->
                                // One source failing (borrowed Android code can throw Errors on desktop) leaves it out
                                // instead of ending this collector, which would leave the manager uninitialized
                                guarded("source ${source::class.qualifiedName} of ${extension.pkgName}") {
                                    if (isHentaiEnabled && source.id in BlacklistedSources.BLACKLISTED_EXT_SOURCES) return@guarded
                                    // SY delegated sources (Pururin, MangaDex, 8Muses, NHentai, LANraragi), like Android
                                    val wrapped = DesktopDelegatedSources.wrap(source, context)
                                    val stub = StubSource.from(wrapped)
                                    map[wrapped.id] = wrapped
                                    registerStubSource(stub)
                                }
                            }
                        }
                        // Mangayomi JavaScript sources (extension sources win an id clash). Their stubs keep the
                        // "[JS]" name, so an uninstalled source keeps its download folder.
                        js.forEach {
                            guarded("JavaScript source ${it::class.qualifiedName}") {
                                if (it.id in map) return@guarded
                                val stub = if (it is MangayomiJsSource) StubSource(id = it.id, lang = it.lang, name = it.stubName) else StubSource.from(it)
                                map[it.id] = it
                                registerStubSource(stub)
                            }
                        }
                        guarded("publishing the delegated source ids") { DesktopDelegatedSources.publishSourceIds(map.values) }
                        sourcesMap.value = map
                        if (initialized) _isInitialized.value = true
                    }
            }
        }
    }

    /** [block]'s result, or `null` (logged) when it throws anything but a cancellation. */
    private inline fun <T> guarded(what: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        xLogE("Could not load $what", e)
        null
    }

    private data class Loaded(
        val extensions: List<Extension.Installed>,
        val initialized: Boolean,
        val isHentaiEnabled: Boolean,
        val jsSources: List<Source>,
    )

    override fun get(sourceKey: Long): Source? = sourcesMap.value[sourceKey]

    override fun getOrStub(sourceKey: Long): Source = get(sourceKey) ?: stubSources.getOrPut(sourceKey) {
        runBlocking { createStubSource(sourceKey) }
    }

    override fun getAll(): List<Source> = sourcesMap.value.values.toList()

    override fun getOnlineSources(): List<HttpSource> = sourcesMap.value.values.filterIsInstance<HttpSource>()

    override fun getVisibleOnlineSources(): List<HttpSource> =
        getOnlineSources().filter { it.id !in BlacklistedSources.HIDDEN_SOURCES }

    override fun getVisibleSources(): List<Source> = getAll().filter { it.id !in BlacklistedSources.HIDDEN_SOURCES }

    override suspend fun getMergedSources(mangaId: Long): List<Source> =
        Injekt.get<GetMergedReferencesById>().await(mangaId)
            .distinctBy { it.mangaSourceId }
            .map { getOrStub(it.mangaSourceId) }

    override fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }.toSet()
        return stubSources.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubSource) {
        scope.launch {
            val dbSource = stubSourceRepository.getStubSource(source.id)
            if (dbSource == source) return@launch
            stubSourceRepository.upsertStubSource(source.id, source.lang, source.name)
        }
    }

    private suspend fun createStubSource(id: Long): StubSource {
        stubSourceRepository.getStubSource(id)?.let { return it }
        extensionManager?.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubSource(id = id, lang = "", name = "")
    }
}
