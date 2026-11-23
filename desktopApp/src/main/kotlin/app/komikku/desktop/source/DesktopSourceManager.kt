package app.komikku.desktop.source

import android.content.Context
import app.komikku.shared.extension.PlatformExtensionManager
import app.komikku.sourcejs.mihon.MangayomiJsSource
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.source.BlacklistedSources
import exh.source.ExhPreferences
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
 * extensions' sources; their stubs are named [MangayomiJsSource.stubName].
 *
 * Not on desktop (yet): SY's built-in E-Hentai / merged sources and delegated (enhanced) sources,
 * which live in the Android app.
 */
class DesktopSourceManager(
    context: Context,
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

    private val _isInitialized = MutableStateFlow(extensionManager == null)
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
                            extension.sources
                                .filterNot { isHentaiEnabled && it.id in BlacklistedSources.BLACKLISTED_EXT_SOURCES }
                                .forEach {
                                    map[it.id] = it
                                    registerStubSource(StubSource.from(it))
                                }
                        }
                        // Mangayomi JavaScript sources (extension sources win an id clash). Their stubs keep the
                        // "[JS]" name, so an uninstalled source keeps its download folder.
                        js.forEach {
                            if (it.id in map) return@forEach
                            map[it.id] = it
                            registerStubSource(
                                if (it is MangayomiJsSource) StubSource(id = it.id, lang = it.lang, name = it.stubName) else StubSource.from(it),
                            )
                        }
                        sourcesMap.value = map
                        if (initialized) _isInitialized.value = true
                    }
            }
        }
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
