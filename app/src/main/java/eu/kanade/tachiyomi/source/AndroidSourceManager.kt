@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.source.online.all.Lanraragi
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.Pururin
import eu.kanade.tachiyomi.source.online.english.EightMuses
import exh.log.xLogD
import exh.source.BlacklistedSources
import exh.source.DelegatedHttpSource
import exh.source.EHENTAI_EXT_SOURCES
import exh.source.EIGHTMUSES_SOURCE_ID
import exh.source.EXHENTAI_EXT_SOURCES
import exh.source.EnhancedHttpSource
import exh.source.ExhPreferences
import exh.source.MERGED_SOURCE_ID
import exh.source.handleSourceLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class AndroidSourceManager(
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val sourceRepository: StubSourceRepository,
) : SourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val downloadManager: DownloadManager by injectLazy()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, Source>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubSource>()

    override val sources: Flow<List<Source>> = sourcesMapFlow.map { it.values.toList() }

    // SY -->
    private val exhPreferences: ExhPreferences by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()
    // SY <--
    // KMK -->
    private val getMergedReferencesById: GetMergedReferencesById by injectLazy()
    // KMK <--

    init {
        scope.launch {
            extensionManager.installedExtensionsFlow
                // SY -->
                .combine(exhPreferences.enableExhentai().changes()) { extensions, enableExhentai ->
                    extensions to enableExhentai
                }
                // KMK -->
                .combine(
                    exhPreferences.isHentaiEnabled().changes(),
                ) { (a, b), c -> Triple(a, b, c) }
                // KMK <--
                // SY <--
                .collectLatest { (extensions, enableExhentai/* KMK --> */, isHentaiEnabled/* KMK <-- */) ->
                    val mutableMap = ConcurrentHashMap<Long, Source>(
                        mapOf(
                            LocalSource.ID to LocalSource(
                                context,
                                Injekt.get(),
                                Injekt.get(),
                                // SY -->
                                sourcePreferences.allowLocalSourceHiddenFolders()::get,
                                // SY <--
                            ),
                        ),
                    ).apply {
                        // KMK -->
                        if (isHentaiEnabled) {
                            EHENTAI_EXT_SOURCES.forEach { (id, lang) ->
                                put(id, EHentai(id, false, context, lang))
                            }
                            if (enableExhentai) {
                                EXHENTAI_EXT_SOURCES.forEach { (id, lang) ->
                                    put(id, EHentai(id, true, context, lang))
                                }
                            }
                        }
                        // KMK <--
                        // SY -->
                        put(MERGED_SOURCE_ID, MergedSource())
                        // SY <--
                    }
                    extensions.forEach { extension ->
                        extension.sources.mapNotNull { it.toInternalSource(/* KMK --> */isHentaiEnabled/* KMK <-- */) }.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(StubSource.from(it))
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                    _isInitialized.value = true
                }
        }

        scope.launch {
            sourceRepository.subscribeAll()
                .collectLatest { sources ->
                    val mutableMap = stubSourcesMap.toMutableMap()
                    sources.forEach {
                        mutableMap[it.id] = it
                    }
                }
        }
    }

    private fun Source.toInternalSource(
        // KMK -->
        isHentaiEnabled: Boolean,
        // KMK <--
    ): Source? {
        // EXH -->
        val sourceQName = this::class.qualifiedName
        val delegate = if (sourceQName != null) {
            // KMK -->
            DELEGATED_SOURCES.firstOrNull { delegated ->
                sourceQName == delegated.originalSourceQualifiedClassName ||
                    (delegated.factory && sourceQName.startsWith(delegated.originalSourceQualifiedClassName))
            }
            // KMK <--
        } else {
            null
        }
        val newSource = if (this is HttpSource && delegate != null) {
            xLogD("Delegating source: %s -> %s!", sourceQName, delegate.newSourceClass.qualifiedName)
            val enhancedSource = EnhancedHttpSource(
                this,
                delegate.newSourceClass.constructors.find { it.parameters.size == 2 }!!.call(this, context),
            )

            currentDelegatedSources[enhancedSource.originalSource.id] = DelegatedSource(
                enhancedSource.originalSource.name,
                enhancedSource.originalSource.id,
                enhancedSource.originalSource::class.qualifiedName ?: delegate.originalSourceQualifiedClassName,
                (enhancedSource.enhancedSource as DelegatedHttpSource)::class,
                delegate.factory,
            )
            enhancedSource
        } else {
            this
        }

        return if (
            // KMK -->
            isHentaiEnabled &&
            // KMK <--
            id in BlacklistedSources.BLACKLISTED_EXT_SOURCES
        ) {
            xLogD(
                "Removing blacklisted source: (id: %s, name: %s, lang: %s)!",
                id,
                name,
                lang,
            )
            null
        } else {
            newSource
        }
        // EXH <--
    }

    override fun get(sourceKey: Long): Source? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): Source {
        return sourcesMapFlow.value[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { createStubSource(sourceKey) }
        }
    }

    override fun getAll() = sourcesMapFlow.value.values.toList()

    override fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<HttpSource>()

    override fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    // SY -->
    override fun getVisibleOnlineSources() = sourcesMapFlow.value.values
        .filterIsInstance<HttpSource>()
        .filter {
            it.id !in BlacklistedSources.HIDDEN_SOURCES
        }

    override fun getVisibleSources() = sourcesMapFlow.value.values
        .filter {
            it.id !in BlacklistedSources.HIDDEN_SOURCES
        }

    fun getDelegatedSources() = sourcesMapFlow.value.values
        .filterIsInstance<EnhancedHttpSource>()
        .mapNotNull { enhancedHttpSource ->
            enhancedHttpSource.enhancedSource as? DelegatedHttpSource
        }
    // SY <--

    // KMK -->
    override suspend fun getMergedSources(mangaId: Long): List<Source> {
        val sources = getMergedReferencesById.await(mangaId)
        return sources.distinctBy { it.mangaSourceId }
            .map { getOrStub(it.mangaSourceId) }
    }
    // KMK <--

    private fun registerStubSource(source: StubSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                downloadManager.renameSource(dbSource, source)
            }
        }
    }

    private suspend fun createStubSource(id: Long): StubSource {
        sourceRepository.getStubSource(id)?.let {
            return it
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubSource(id = id, lang = "", name = "")
    }

    // SY -->
    companion object {
        private const val fillInSourceId = Long.MAX_VALUE

        /*
         * If an extension is declaring sub-classes based on the main class, then set `factory=true` and
         * only put the package without the class name. For example:
         * "eu.kanade.tachiyomi.extension.all.mangadex" instead of "eu.kanade.tachiyomi.extension.all.mangadex.MangaDex"
         */
        val DELEGATED_SOURCES = listOf(
            DelegatedSource(
                "Pururin",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.pururin.Pururin",
                Pururin::class,
            ),
            DelegatedSource(
                "MangaDex",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.mangadex.MangaDex",
                MangaDex::class,
            ),
            DelegatedSource(
                "8Muses",
                EIGHTMUSES_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.eightmuses.EightMuses",
                EightMuses::class,
            ),
            DelegatedSource(
                "NHentai",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.nhentai.NHentai",
                NHentai::class,
            ),
            DelegatedSource(
                "LANraragi",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.lanraragi.LANraragi",
                Lanraragi::class,
            ),
        )

        val currentDelegatedSources: MutableMap<Long, DelegatedSource> =
            ListenMutableMap(mutableMapOf(), ::handleSourceLibrary)

        data class DelegatedSource(
            val sourceName: String,
            val sourceId: Long,
            val originalSourceQualifiedClassName: String,
            val newSourceClass: KClass<out DelegatedHttpSource>,
            val factory: Boolean = false,
        )
    }

    private class ListenMutableMap<K, V>(
        private val internalMap: MutableMap<K, V>,
        private val listener: () -> Unit,
    ) : MutableMap<K, V> by internalMap {
        override fun clear() {
            val clearResult = internalMap.clear()
            listener()
            return clearResult
        }

        override fun put(key: K, value: V): V? {
            val putResult = internalMap.put(key, value)
            if (putResult == null) {
                listener()
            }
            return putResult
        }

        override fun putAll(from: Map<out K, V>) {
            internalMap.putAll(from)
            listener()
        }

        override fun remove(key: K): V? {
            val removeResult = internalMap.remove(key)
            if (removeResult != null) {
                listener()
            }
            return removeResult
        }
    }

    // SY <--
}
