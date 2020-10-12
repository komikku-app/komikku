package eu.kanade.tachiyomi.data.backup.legacy.serializer

import com.github.salomonbrys.kotson.typeAdapter
import com.google.gson.TypeAdapter
import exh.merged.sql.models.MergedMangaReference

/**
 * JSON Serializer used to write / read [MergedMangaReference] to / from json
 */
object MergedMangaReferenceTypeAdapter {

    fun build(): TypeAdapter<MergedMangaReference> {
        return typeAdapter {
            write {
                beginArray()
                value(it.mangaUrl)
                value(it.mergeUrl)
                value(it.mangaSourceId)
                value(it.chapterSortMode)
                value(it.chapterPriority)
                value(it.getChapterUpdates)
                value(it.isInfoManga)
                value(it.downloadChapters)
                endArray()
            }

            read {
                beginArray()
                MergedMangaReference(
                    id = null,
                    mangaUrl = nextString(),
                    mergeUrl = nextString(),
                    mangaSourceId = nextLong(),
                    chapterSortMode = nextInt(),
                    chapterPriority = nextInt(),
                    getChapterUpdates = nextBoolean(),
                    isInfoManga = nextBoolean(),
                    downloadChapters = nextBoolean(),
                    mangaId = null,
                    mergeId = null
                )
            }
        }
    }
}
