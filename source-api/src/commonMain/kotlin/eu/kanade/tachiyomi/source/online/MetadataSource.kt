package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.sql.models.SearchMetadata
import rx.Completable
import rx.Single
import tachiyomi.core.common.util.lang.awaitSingle
import tachiyomi.core.common.util.lang.runAsObservable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.reflect.KClass

/**
 * LEWD!
 */
interface MetadataSource<M : RaisedSearchMetadata, I> : CatalogueSource {
    interface GetMangaId {
        suspend fun awaitId(url: String, sourceId: Long): Long?
    }
    interface InsertFlatMetadata {
        suspend fun await(metadata: RaisedSearchMetadata)
    }
    interface GetFlatMetadataById {
        suspend fun await(id: Long): FlatMetadata?
        suspend fun await(ids: List<Long>): Map<Long, FlatMetadata>
        suspend fun awaitSearchMetadata(ids: List<Long>): Map<Long, SearchMetadata>
    }
    val getMangaId: GetMangaId get() = Injekt.get()
    val insertFlatMetadata: InsertFlatMetadata get() = Injekt.get()
    val getFlatMetadataById: GetFlatMetadataById get() = Injekt.get()

    /**
     * The class of the metadata used by this source
     */
    val metaClass: KClass<M>

    /**
     * Parse the supplied input into the supplied metadata object
     */
    suspend fun parseIntoMetadata(metadata: M, input: I)

    /**
     * Use reflection to create a new instance of metadata
     */
    fun newMetaInstance(): M

    /**
     * Parses metadata from the input and then copies it into the manga
     *
     * Will also save the metadata to the DB if possible
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use the MangaInfo variant")
    fun parseToMangaCompletable(manga: SManga, input: I): Completable = runAsObservable {
        parseToManga(manga, input)
    }.toCompletable()

    suspend fun parseToManga(manga: SManga, input: I): SManga {
        val mangaId = manga.id()
        val metadata = if (mangaId != null) {
            val flatMetadata = getFlatMetadataById.await(mangaId)
            flatMetadata?.raise(metaClass) ?: newMetaInstance()
        } else {
            newMetaInstance()
        }

        parseIntoMetadata(metadata, input)
        if (mangaId != null) {
            metadata.mangaId = mangaId
            insertFlatMetadata.await(metadata)
        }

        return metadata.createMangaInfo(manga)
    }

    /**
     * Try to first get the metadata from the DB. If the metadata is not in the DB, calls the input
     * producer and parses the metadata from the input
     *
     * If the metadata needs to be parsed from the input producer, the resulting parsed metadata will
     * also be saved to the DB.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("use fetchOrLoadMetadata made for MangaInfo")
    fun getOrLoadMetadata(mangaId: Long?, inputProducer: () -> Single<I>): Single<M> =
        runAsObservable {
            fetchOrLoadMetadata(mangaId) { inputProducer().toObservable().awaitSingle() }
        }.toSingle()

    /**
     * Try to first get the metadata from the DB. If the metadata is not in the DB, calls the input
     * producer and parses the metadata from the input
     *
     * If the metadata needs to be parsed from the input producer, the resulting parsed metadata will
     * also be saved to the DB.
     */
    suspend fun fetchOrLoadMetadata(mangaId: Long?, inputProducer: suspend () -> I): M {
        val meta = if (mangaId != null) {
            val flatMetadata = getFlatMetadataById.await(mangaId)
            flatMetadata?.raise(metaClass)
        } else {
            null
        }

        return meta ?: inputProducer().let { input ->
            val newMeta = newMetaInstance()
            parseIntoMetadata(newMeta, input)
            if (mangaId != null) {
                newMeta.mangaId = mangaId
                insertFlatMetadata.await(newMeta)
            }
            newMeta
        }
    }

    suspend fun SManga.id() = getMangaId.awaitId(url, id)
}
