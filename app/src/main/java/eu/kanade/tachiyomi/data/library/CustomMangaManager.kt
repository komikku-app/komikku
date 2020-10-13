package eu.kanade.tachiyomi.data.library

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
            Json.decodeFromString<MangaList>(
                Scanner(editJson).useDelimiter("\\Z").next()
            )
        } catch (e: Exception) {
            null
        } ?: return

        val mangasJson = json.mangas ?: return
        customMangaMap = mangasJson.mapNotNull { mangaJson ->
            val id = mangaJson.id ?: return@mapNotNull null
            val manga = MangaImpl().apply {
                this.id = id
                title = mangaJson.title ?: ""
                author = mangaJson.author
                artist = mangaJson.artist
                description = mangaJson.description
                genre = mangaJson.genre?.joinToString(", ")
            }
            id to manga
        }.toMap().toMutableMap()
    }

    fun saveMangaInfo(manga: MangaJson) {
        if (manga.title == null && manga.author == null && manga.artist == null && manga.description == null && manga.genre == null) {
            customMangaMap.remove(manga.id!!)
        } else {
            customMangaMap[manga.id!!] = MangaImpl().apply {
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
            editJson.delete()
            editJson.writeText(Json.encodeToString(MangaList(jsonElements)))
        }
    }

    private fun Manga.toJson(): MangaJson {
        return MangaJson(
            id!!,
            title,
            author,
            artist,
            description,
            genre?.split(", ")
        )
    }

    @Serializable
    data class MangaList(
        val mangas: List<MangaJson>? = null
    )

    @Serializable
    data class MangaJson(
        val id: Long? = null,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null
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
