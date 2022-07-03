package exh.metadata.metadata.base

import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
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
