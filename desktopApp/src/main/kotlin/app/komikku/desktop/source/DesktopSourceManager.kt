package app.komikku.desktop.source

import android.content.Context
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
 * Desktop [SourceManager]: the local source only, until the desktop extension runtime (Phase 5)
 * registers extension sources. Unknown ids resolve to [StubSource]s from the database, like
 * `AndroidSourceManager` (no extension metadata fallback yet).
 */
class DesktopSourceManager(
    context: Context,
    fileSystem: LocalSourceFileSystem,
    coverManager: LocalCoverManager,
    private val stubSourceRepository: StubSourceRepository,
    allowHiddenFiles: () -> Boolean,
) : SourceManager {

    private val localSource = LocalSource(context, fileSystem, coverManager, allowHiddenFiles)

    private val sourcesMap = MutableStateFlow<Map<Long, Source>>(mapOf(LocalSource.ID to localSource))

    private val stubSources = ConcurrentHashMap<Long, StubSource>()

    override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    override val sources: Flow<List<Source>> = sourcesMap.map { it.values.toList() }

    override fun get(sourceKey: Long): Source? = sourcesMap.value[sourceKey]

    override fun getOrStub(sourceKey: Long): Source = get(sourceKey) ?: stubSources.getOrPut(sourceKey) {
        runBlocking { stubSourceRepository.getStubSource(sourceKey) } ?: StubSource(id = sourceKey, lang = "", name = "")
    }

    override fun getAll(): List<Source> = sourcesMap.value.values.toList()

    override fun getOnlineSources(): List<HttpSource> = sourcesMap.value.values.filterIsInstance<HttpSource>()

    override fun getVisibleOnlineSources(): List<HttpSource> = getOnlineSources()

    override fun getVisibleSources(): List<Source> = getAll()

    override suspend fun getMergedSources(mangaId: Long): List<Source> =
        Injekt.get<GetMergedReferencesById>().await(mangaId)
            .distinctBy { it.mangaSourceId }
            .map { getOrStub(it.mangaSourceId) }

    override fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }.toSet()
        return stubSources.values.filterNot { it.id in onlineSourceIds }
    }
}
