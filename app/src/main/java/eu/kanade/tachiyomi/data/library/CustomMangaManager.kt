package eu.kanade.tachiyomi.data.library

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.Scanner

class CustomMangaManager(val context: Context) {

    private val editJson = File(context.getExternalFilesDir(null), "edits.json")

    private var customMangaMap = mutableMapOf<Long, Manga>()

    init {
        fetchCustomData()
    }

    fun getManga(manga: Manga): Manga? = customMangaMap[manga.id]

    private fun fetchCustomData() {
        if (!editJson.exists() || !editJson.isFile) return

        val json = try {
            Json.decodeFromString<JsonObject>(
                Scanner(editJson).useDelimiter("\\Z").next()
            )
        } catch (e: Exception) {
            null
        } ?: return

        val mangasJson = json["mangas"] as? JsonArray ?: return
        customMangaMap = mangasJson.mapNotNull { element ->
            val mangaObject = element as? JsonObject ?: return@mapNotNull null
            val id = mangaObject["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val manga = MangaImpl().apply {
                this.id = id
                title = mangaObject["title"]?.jsonPrimitive?.contentOrNull ?: ""
                author = mangaObject["author"]?.jsonPrimitive?.contentOrNull
                artist = mangaObject["artist"]?.jsonPrimitive?.contentOrNull
                description = mangaObject["description"]?.jsonPrimitive?.contentOrNull
                genre = (mangaObject["genre"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.joinToString(", ")
            }
            id to manga
        }.toMap().toMutableMap()
    }

    fun saveMangaInfo(manga: MangaJson) {
        if (manga.title == null && manga.author == null && manga.artist == null && manga.description == null && manga.genre == null) {
            customMangaMap.remove(manga.id)
        } else {
            customMangaMap[manga.id] = MangaImpl().apply {
                id = manga.id
                title = manga.title ?: ""
                author = manga.author
                artist = manga.artist
                description = manga.description
                genre = manga.genre?.joinToString(", ")
            }
        }
        saveCustomInfo()
    }

    private fun saveCustomInfo() {
        val jsonElements = customMangaMap.values.map { it.toJson() }
        if (jsonElements.isNotEmpty()) {
            val mangaEntries = Json.encodeToJsonElement(jsonElements)
            val root = buildJsonObject {
                put("mangas", mangaEntries)
            }
            editJson.delete()
            editJson.writeText(Json.encodeToString(root))
        }
    }

    private fun Manga.toJson(): MangaJson {
        return MangaJson(
            id!!,
            title,
            author,
            artist,
            description,
            genre?.split(", ")?.toTypedArray()
        )
    }

    @Serializable
    data class MangaJson(
        val id: Long,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: Array<String>? = null
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MangaJson
            if (id != other.id) return false
            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }
}
