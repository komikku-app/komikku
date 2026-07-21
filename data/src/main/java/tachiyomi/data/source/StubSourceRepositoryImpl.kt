package tachiyomi.data.source

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.StubSourceRepository

class StubSourceRepositoryImpl(
    private val handler: DatabaseHandler,
) : StubSourceRepository {

    override fun subscribeAll(): Flow<List<StubSource>> {
        return handler.subscribeToList { sourcesQueries.findAll(::mapStubSource) }
    }

    override suspend fun getStubSource(id: Long): StubSource? {
        return handler.awaitOneOrNull { sourcesQueries.findOne(id, ::mapStubSource) }
    }

    override suspend fun upsertStubSource(id: Long, lang: String, name: String) {
        handler.await { sourcesQueries.upsert(id, lang, name) }
    }

    private fun mapStubSource(
        id: Long,
        lang: String,
        name: String,
        // KMK -->
        // sort is required by SQLDelight (findAll returns 4 columns) but unused here —
        // StubSource is a source API impl, not the domain model. Sort is applied
        // when converting to domain Source in SourceRepositoryImpl.getSources().
        @Suppress("UNUSED_PARAMETER") sort: Long,
        // KMK <--
    ): StubSource = StubSource(id = id, lang = lang, name = name)
}
