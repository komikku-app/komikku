package tachiyomi.domain.source.service

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.source.model.StubSource

interface SourceManager {

    val catalogueSources: Flow<List<CatalogueSource>>

    fun get(sourceKey: Long): Source?

    fun getOrStub(sourceKey: Long): Source

    fun getOnlineSources(): List<HttpSource>

    fun getCatalogueSources(): List<CatalogueSource>

    // SY -->
    val isInitialized: StateFlow<Boolean>

    fun getVisibleOnlineSources(): List<HttpSource>

    fun getVisibleCatalogueSources(): List<CatalogueSource>
    // SY <--

    // KMK -->
    suspend fun getMergedSources(mangaId: Long): List<Source>
    // KMK <--

    fun getStubSources(): List<StubSource>
}
