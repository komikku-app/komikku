package exh.metadata.metadata.base

import android.content.Context
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toMangaInfo
import eu.kanade.tachiyomi.source.model.toSManga
import exh.metadata.forEach
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.EightMusesSearchMetadata
import exh.metadata.metadata.HBrowseSearchMetadata
import exh.metadata.metadata.HitomiSearchMetadata
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.NHentaiSearchMetadata
import exh.metadata.metadata.PervEdenSearchMetadata
import exh.metadata.metadata.PururinSearchMetadata
import exh.metadata.metadata.TsuminoSearchMetadata
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import exh.util.plusAssign
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import tachiyomi.source.model.MangaInfo
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Polymorphic
@Serializable
abstract class RaisedSearchMetadata {
    @Transient
    var mangaId: Long = -1

    @Transient
    var uploader: String? = null

    @Transient
    protected open var indexedExtra: String? = null

    @Transient
    val tags = mutableListOf<RaisedTag>()

    @Transient
    val titles = mutableListOf<RaisedTitle>()

    var filteredScanlators: String? = null

    fun getTitleOfType(type: Int): String? = titles.find { it.type == type }?.title

    fun replaceTitleOfType(type: Int, newTitle: String?) {
        titles.removeAll { it.type == type }
        if (newTitle != null) titles += RaisedTitle(newTitle, type)
    }

    open fun copyTo(manga: SManga) {
        val infoManga = createMangaInfo(manga.toMangaInfo()).toSManga()
        manga.copyFrom(infoManga)
    }

    abstract fun createMangaInfo(manga: MangaInfo): MangaInfo

    fun tagsToGenreString() = tags.toGenreString()

    fun tagsToGenreList() = tags.toGenreList()

    fun tagsToDescription() =
        StringBuilder("Tags:\n").apply {
            // BiConsumer only available in Java 8, don't bother calling forEach directly on 'tags'
            val groupedTags = tags.filter { it.type != TAG_TYPE_VIRTUAL }.groupBy {
                it.namespace
            }.entries

            groupedTags.forEach { namespace, tags ->
                if (tags.isNotEmpty()) {
                    val joinedTags = tags.joinToString(separator = " ", transform = { "<${it.name}>" })
                    if (namespace != null) {
                        this += "▪ "
                        this += namespace
                        this += ": "
                    }
                    this += joinedTags
                    this += "\n"
                }
            }
        }

    fun List<RaisedTag>.ofNamespace(ns: String): List<RaisedTag> {
        return filter { it.namespace == ns }
    }

    fun flatten(): FlatMetadata {
        require(mangaId != -1L)

        val extra = raiseFlattenJson.encodeToString(this)
        return FlatMetadata(
            SearchMetadata(
                mangaId,
                uploader,
                extra,
                indexedExtra,
                0
            ),
            tags.map {
                SearchTag(
                    null,
                    mangaId,
                    it.namespace,
                    it.name,
                    it.type
                )
            },
            titles.map {
                SearchTitle(
                    null,
                    mangaId,
                    it.title,
                    it.type
                )
            }
        )
    }

    fun fillBaseFields(metadata: FlatMetadata) {
        mangaId = metadata.metadata.mangaId
        uploader = metadata.metadata.uploader
        indexedExtra = metadata.metadata.indexedExtra

        this.tags.clear()
        this.tags += metadata.tags.map {
            RaisedTag(it.namespace, it.name, it.type)
        }

        this.titles.clear()
        this.titles += metadata.titles.map {
            RaisedTitle(it.title, it.type)
        }
    }

    abstract fun getExtraInfoPairs(context: Context): List<Pair<String, String>>

    companion object {
        // Virtual tags allow searching of otherwise unindexed fields
        const val TAG_TYPE_VIRTUAL = -2

        fun MutableList<RaisedTag>.toGenreString() =
            (this).filter { it.type != TAG_TYPE_VIRTUAL }
                .joinToString { (if (it.namespace != null) "${it.namespace}: " else "") + it.name }

        fun MutableList<RaisedTag>.toGenreList() =
            (this).filter { it.type != TAG_TYPE_VIRTUAL }
                .map { (if (it.namespace != null) "${it.namespace}: " else "") + it.name }

        private val module = SerializersModule {
            polymorphic(RaisedSearchMetadata::class) {
                subclass(EHentaiSearchMetadata::class)
                subclass(EightMusesSearchMetadata::class)
                subclass(HBrowseSearchMetadata::class)
                subclass(HitomiSearchMetadata::class)
                subclass(MangaDexSearchMetadata::class)
                subclass(NHentaiSearchMetadata::class)
                subclass(PervEdenSearchMetadata::class)
                subclass(PururinSearchMetadata::class)
                subclass(TsuminoSearchMetadata::class)
            }
        }

        val raiseFlattenJson = Json {
            ignoreUnknownKeys = true
            serializersModule = module
        }

        fun titleDelegate(type: Int) = object : ReadWriteProperty<RaisedSearchMetadata, String?> {
            /**
             * Returns the value of the property for the given object.
             * @param thisRef the object for which the value is requested.
             * @param property the metadata for the property.
             * @return the property value.
             */
            override fun getValue(thisRef: RaisedSearchMetadata, property: KProperty<*>) =
                thisRef.getTitleOfType(type)

            /**
             * Sets the value of the property for the given object.
             * @param thisRef the object for which the value is requested.
             * @param property the metadata for the property.
             * @param value the value to set.
             */
            override fun setValue(thisRef: RaisedSearchMetadata, property: KProperty<*>, value: String?) =
                thisRef.replaceTitleOfType(type, value)
        }
    }
}
