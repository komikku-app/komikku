package exh.metadata.metadata.base

import com.pushtorefresh.storio.operations.PreparedOperation
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.util.lang.runAsObservable
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.util.executeOnIO
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import rx.Completable
import rx.Single
import kotlin.reflect.KClass

@Serializable
data class FlatMetadata(
    val metadata: SearchMetadata,
    val tags: List<SearchTag>,
    val titles: List<SearchTitle>
) {
    inline fun <reified T : RaisedSearchMetadata> raise(): T = raise(T::class)

    @OptIn(InternalSerializationApi::class)
    fun <T : RaisedSearchMetadata> raise(clazz: KClass<T>): T =
        RaisedSearchMetadata.raiseFlattenJson
            .decodeFromString(clazz.serializer(), metadata.extra).apply {
                fillBaseFields(this@FlatMetadata)
            }
}

interface PreparedSuspendOperation<T> : PreparedOperation<T> {
    /**
     * Creates a [Flow] that emits the result of of Operation
     *
     * Example:
     *  override fun asFlow(): Flow<T> = flow { emit(operation()) }
     *
     */
    fun asFlow(): Flow<T>

    /**
     * Executes operation asynchronously in the I/O thread pool.
     */
    suspend fun executeOnIO(): T
}

fun DatabaseHelper.getFlatMetadataForManga(mangaId: Long): PreparedSuspendOperation<FlatMetadata?> =
    preparedOperationFromSuspend {
        val meta = getSearchMetadataForManga(mangaId).executeOnIO()
        if (meta != null) {
            val tags = getSearchTagsForManga(mangaId).executeOnIO()
            val titles = getSearchTitlesForManga(mangaId).executeOnIO()

            FlatMetadata(meta, tags, titles)
        } else null
    }

private fun <T> preparedOperationFromSuspend(operation: suspend () -> T): PreparedSuspendOperation<T> {
    return object : PreparedSuspendOperation<T> {
        /**
         * Creates [rx.Observable] that emits result of Operation.
         *
         *
         * Observable may be "Hot" or "Cold", please read documentation of the concrete implementation.
         *
         * @return observable result of operation with only one [rx.Observer.onNext] call.
         */
        override fun createObservable() = runAsObservable(operation)

        /**
         * Executes operation synchronously in current thread.
         *
         *
         * Notice: Blocking I/O operation should not be executed on the Main Thread,
         * it can cause ANR (Activity Not Responding dialog), block the UI and drop animations frames.
         * So please, execute blocking I/O operation only from background thread.
         * See [androidx.annotation.WorkerThread].
         *
         * @return nullable result of operation.
         */
        override fun executeAsBlocking() = runBlocking { operation() }

        /**
         * Creates [rx.Observable] that emits result of Operation.
         *
         *
         * Observable may be "Hot" (usually "Warm") or "Cold", please read documentation of the concrete implementation.
         *
         * @return observable result of operation with only one [rx.Observer.onNext] call.
         */
        override fun asRxObservable() = runAsObservable(operation)

        /**
         * Creates [rx.Single] that emits result of Operation lazily when somebody subscribes to it.
         *
         *
         *
         * @return single result of operation.
         */
        override fun asRxSingle(): Single<T> = runAsObservable(operation).toSingle()

        /**
         * Creates a [Flow] that emits the result of of Operation
         *
         * Example:
         *  override fun asFlow(): Flow<T> = flow { emit(operation()) }
         *
         */
        override fun asFlow(): Flow<T> = flow { emit(operation()) }

        /**
         * Executes operation asynchronously in the I/O thread pool.
         */
        override suspend fun executeOnIO(): T = withIOContext { operation() }
    }
}

fun DatabaseHelper.insertFlatMetadata(flatMetadata: FlatMetadata): Completable = Completable.fromCallable {
    require(flatMetadata.metadata.mangaId != -1L)

    inTransaction {
        insertSearchMetadata(flatMetadata.metadata).executeAsBlocking()
        setSearchTagsForManga(flatMetadata.metadata.mangaId, flatMetadata.tags)
        setSearchTitlesForManga(flatMetadata.metadata.mangaId, flatMetadata.titles)
    }
}

suspend fun DatabaseHelper.insertFlatMetadataAsync(flatMetadata: FlatMetadata): Deferred<Unit> = coroutineScope {
    async {
        require(flatMetadata.metadata.mangaId != -1L)

        inTransaction {
            insertSearchMetadata(flatMetadata.metadata).executeOnIO()
            setSearchTagsForManga(flatMetadata.metadata.mangaId, flatMetadata.tags)
            setSearchTitlesForManga(flatMetadata.metadata.mangaId, flatMetadata.titles)
        }
    }
}
