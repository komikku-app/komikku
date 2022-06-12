package exh.metadata.metadata.base

import com.pushtorefresh.storio.operations.PreparedOperation
import eu.kanade.data.AndroidDatabaseHandler
import eu.kanade.data.DatabaseHandler
import eu.kanade.data.exh.searchMetadataMapper
import eu.kanade.data.exh.searchTagMapper
import eu.kanade.data.exh.searchTitleMapper
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val titles: List<SearchTitle>,
) {
    inline fun <reified T : RaisedSearchMetadata> raise(): T = raise(T::class)

    @OptIn(InternalSerializationApi::class)
    fun <T : RaisedSearchMetadata> raise(clazz: KClass<T>): T =
        RaisedSearchMetadata.raiseFlattenJson
            .decodeFromString(clazz.serializer(), metadata.extra).apply {
                fillBaseFields(this@FlatMetadata)
            }
}

fun DatabaseHandler.getFlatMetadataForManga(mangaId: Long): FlatMetadata? {
    this as AndroidDatabaseHandler
    val meta = db.search_metadataQueries.selectByMangaId(mangaId, searchMetadataMapper).executeAsOneOrNull()
    return if (meta != null) {
        val tags = db.search_tagsQueries.selectByMangaId(mangaId, searchTagMapper).executeAsList()
        val titles = db.search_titlesQueries.selectByMangaId(mangaId, searchTitleMapper).executeAsList()

        FlatMetadata(meta, tags, titles)
    } else null
}

suspend fun DatabaseHandler.awaitFlatMetadataForManga(mangaId: Long): FlatMetadata? {
    return await {
        val meta = search_metadataQueries.selectByMangaId(mangaId, searchMetadataMapper).executeAsOneOrNull()
        if (meta != null) {
            val tags = search_tagsQueries.selectByMangaId(mangaId, searchTagMapper).executeAsList()
            val titles = search_titlesQueries.selectByMangaId(mangaId, searchTitleMapper).executeAsList()

            FlatMetadata(meta, tags, titles)
        } else null
    }
}

fun DatabaseHelper.getFlatMetadataForManga(mangaId: Long): PreparedOperation<FlatMetadata?> {
    // We have to use fromCallable because StorIO messes up the thread scheduling if we use their rx functions
    val single = Single.fromCallable {
        val meta = getSearchMetadataForManga(mangaId).executeAsBlocking()
        if (meta != null) {
            val tags = getSearchTagsForManga(mangaId).executeAsBlocking()
            val titles = getSearchTitlesForManga(mangaId).executeAsBlocking()

            FlatMetadata(meta, tags, titles)
        } else null
    }

    return preparedOperationFromSingle(single)
}

private fun <T> preparedOperationFromSingle(single: Single<T>): PreparedOperation<T> {
    return object : PreparedOperation<T> {
        /**
         * Creates [rx.Observable] that emits result of Operation.
         *
         *
         * Observable may be "Hot" or "Cold", please read documentation of the concrete implementation.
         *
         * @return observable result of operation with only one [rx.Observer.onNext] call.
         */
        override fun createObservable() = single.toObservable()

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
        override fun executeAsBlocking() = single.toBlocking().value()

        /**
         * Creates [rx.Observable] that emits result of Operation.
         *
         *
         * Observable may be "Hot" (usually "Warm") or "Cold", please read documentation of the concrete implementation.
         *
         * @return observable result of operation with only one [rx.Observer.onNext] call.
         */
        override fun asRxObservable() = single.toObservable()

        /**
         * Creates [rx.Single] that emits result of Operation lazily when somebody subscribes to it.
         *
         *
         *
         * @return single result of operation.
         */
        override fun asRxSingle() = single
    }
}

fun DatabaseHandler.insertFlatMetadata(flatMetadata: FlatMetadata) {
    require(flatMetadata.metadata.mangaId != -1L)

    this as AndroidDatabaseHandler // todo remove when legacy backup is dead
    db.transaction {
        flatMetadata.metadata.let {
            db.search_metadataQueries.upsert(it.mangaId, it.uploader, it.extra, it.indexedExtra, it.extraVersion)
        }
        db.search_tagsQueries.deleteByManga(flatMetadata.metadata.mangaId)
        flatMetadata.tags.forEach {
            db.search_tagsQueries.insert(it.mangaId, it.namespace, it.name, it.type)
        }
        db.search_titlesQueries.deleteByManga(flatMetadata.metadata.mangaId)
        flatMetadata.titles.forEach {
            db.search_titlesQueries.insert(it.mangaId, it.title, it.type)
        }
    }
}

suspend fun DatabaseHandler.awaitInsertFlatMetadata(flatMetadata: FlatMetadata) {
    require(flatMetadata.metadata.mangaId != -1L)

    await(true) {
        flatMetadata.metadata.run {
            search_metadataQueries.upsert(mangaId, uploader, extra, indexedExtra, extraVersion)
        }
        search_tagsQueries.deleteByManga(flatMetadata.metadata.mangaId)
        flatMetadata.tags.forEach {
            search_tagsQueries.insert(it.mangaId, it.namespace, it.name, it.type)
        }
        search_titlesQueries.deleteByManga(flatMetadata.metadata.mangaId)
        flatMetadata.titles.forEach {
            search_titlesQueries.insert(it.mangaId, it.title, it.type)
        }
    }
}

fun DatabaseHelper.insertFlatMetadata(flatMetadata: FlatMetadata) {
    require(flatMetadata.metadata.mangaId != -1L)

    inTransaction {
        insertSearchMetadata(flatMetadata.metadata).executeAsBlocking()
        setSearchTagsForManga(flatMetadata.metadata.mangaId, flatMetadata.tags)
        setSearchTitlesForManga(flatMetadata.metadata.mangaId, flatMetadata.titles)
    }
}

fun DatabaseHelper.insertFlatMetadataCompletable(flatMetadata: FlatMetadata): Completable = Completable.fromCallable {
    insertFlatMetadata(flatMetadata)
}

suspend fun DatabaseHelper.insertFlatMetadataAsync(flatMetadata: FlatMetadata): Deferred<Unit> = coroutineScope {
    async {
        require(flatMetadata.metadata.mangaId != -1L)

        inTransaction {
            insertSearchMetadata(flatMetadata.metadata).executeAsBlocking()
            setSearchTagsForManga(flatMetadata.metadata.mangaId, flatMetadata.tags)
            setSearchTitlesForManga(flatMetadata.metadata.mangaId, flatMetadata.titles)
        }
    }
}
