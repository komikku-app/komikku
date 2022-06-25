package eu.kanade.tachiyomi.data.library

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Manga
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import eu.kanade.domain.manga.model.Manga as DomainManga

class CustomMangaManager(val context: Context) {

    private val editJson = File(context.getExternalFilesDir(null), "edits.json")

    private val customMangaMap = fetchCustomData()

    fun getManga(manga: Manga): CustomMangaInfo? = customMangaMap[manga.id]
    fun getManga(manga: DomainManga): CustomMangaInfo? = customMangaMap[manga.id]
    fun getManga(mangaId: Long): CustomMangaInfo? = customMangaMap[mangaId]

    private fun fetchCustomData(): MutableMap<Long, CustomMangaInfo> {
        if (!editJson.exists() || !editJson.isFile) return mutableMapOf()

        val json = try {
            Json.decodeFromString<MangaList>(
                editJson.bufferedReader().use { it.readText() },
            )
        } catch (e: Exception) {
            null
        } ?: return mutableMapOf()

        val mangasJson = json.mangas ?: return mutableMapOf()
        return mangasJson
            .mapNotNull { mangaJson ->
                val id = mangaJson.id ?: return@mapNotNull null
                id to mangaJson.toManga()
            }
            .toMap()
            .toMutableMap()
    }

    fun saveMangaInfo(manga: MangaJson) {
        if (
            manga.title == null &&
            manga.author == null &&
            manga.artist == null &&
            manga.description == null &&
            manga.genre == null &&
            manga.status == null
        ) {
            customMangaMap.remove(manga.id!!)
        } else {
            customMangaMap[manga.id!!] = manga.toManga()
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

    @Serializable
    data class MangaList(
        val mangas: List<MangaJson>? = null,
    )

    @Serializable
    data class MangaJson(
        var id: Long? = null,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
    ) {

        fun toManga() = CustomMangaInfo(
            id = this@MangaJson.id!!,
            title = this@MangaJson.title?.takeUnless { it.isBlank() },
            author = this@MangaJson.author,
            artist = this@MangaJson.artist,
            description = this@MangaJson.description,
            genre = this@MangaJson.genre,
            status = this@MangaJson.status?.takeUnless { it == 0L },
        )
    }

    data class CustomMangaInfo(
        var id: Long,
        val title: String?,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
    ) {
        val genreString by lazy {
            genre?.joinToString()
        }
        val statusLong = status?.toLong()

        fun toJson(): MangaJson {
            return MangaJson(
                id,
                title,
                author,
                artist,
                description,
                genre,
                status,
            )
        }
    }
}
