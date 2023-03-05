package tachiyomi.domain.source.service

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.StubSource

interface SourceManager {

    val catalogueSources: Flow<List<CatalogueSource>>

    fun get(sourceKey: Long): Source?

    fun getOrStub(sourceKey: Long): Source

    fun getOnlineSources(): List<HttpSource>

    fun getCatalogueSources(): List<CatalogueSource>

    // SY -->
    fun getVisibleOnlineSources(): List<HttpSource>

    fun getVisibleCatalogueSources(): List<CatalogueSource>
    // SY <--

    fun getStubSources(): List<StubSource>
}
