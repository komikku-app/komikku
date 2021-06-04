package eu.kanade.tachiyomi.data.backup.legacy.serializer

import exh.merged.sql.models.MergedMangaReference
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * JSON Serializer used to write / read [MergedMangaReference] to / from json
 */
object MergedMangaTypeSerializer : KSerializer<MergedMangaReference> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Manga")

    override fun serialize(encoder: Encoder, value: MergedMangaReference) {
        encoder as JsonEncoder
        encoder.encodeJsonElement(
            buildJsonArray {
                add(value.mangaUrl)
                add(value.mergeUrl)
                add(value.mangaSourceId)
                add(value.chapterSortMode)
                add(value.chapterPriority)
                add(value.getChapterUpdates)
                add(value.isInfoManga)
                add(value.downloadChapters)
            }
        )
    }

    override fun deserialize(decoder: Decoder): MergedMangaReference {
        decoder as JsonDecoder
        val array = decoder.decodeJsonElement().jsonArray
        return MergedMangaReference(
            id = null,
            mangaUrl = array[0].jsonPrimitive.content,
            mergeUrl = array[1].jsonPrimitive.content,
            mangaSourceId = array[2].jsonPrimitive.long,
            chapterSortMode = array[3].jsonPrimitive.int,
            chapterPriority = array[4].jsonPrimitive.int,
            getChapterUpdates = array[5].jsonPrimitive.boolean,
            isInfoManga = array[6].jsonPrimitive.boolean,
            downloadChapters = array[7].jsonPrimitive.boolean,
            mangaId = null,
            mergeId = null
        )
    }
}
