package tachiyomi.data.manga

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository
import java.io.File

class CustomMangaRepositoryImpl(context: Context) : CustomMangaRepository {
    private val editJson = File(context.getExternalFilesDir(null), "edits.json")

    private val customMangaMap = fetchCustomData()

    override fun get(mangaId: Long) = customMangaMap[mangaId]

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

    override fun set(mangaInfo: CustomMangaInfo) {
        if (
            mangaInfo.title == null &&
            mangaInfo.author == null &&
            mangaInfo.artist == null &&
            mangaInfo.thumbnailUrl == null &&
            mangaInfo.description == null &&
            mangaInfo.genre == null &&
            mangaInfo.status == null &&
            // KMK -->
            mangaInfo.incognitoMode != true
            // KMK <--
        ) {
            customMangaMap.remove(mangaInfo.id)
        } else {
            customMangaMap[mangaInfo.id] = mangaInfo
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
        val thumbnailUrl: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
        // KMK -->
        val incognitoMode: Boolean? = null,
        // KMK <--
    ) {

        fun toManga() = CustomMangaInfo(
            id = this@MangaJson.id!!,
            title = this@MangaJson.title?.takeUnless { it.isBlank() },
            author = this@MangaJson.author,
            artist = this@MangaJson.artist,
            thumbnailUrl = this@MangaJson.thumbnailUrl,
            description = this@MangaJson.description,
            genre = this@MangaJson.genre,
            status = this@MangaJson.status?.takeUnless { it == 0L },
            // KMK -->
            incognitoMode = this@MangaJson.incognitoMode,
            // KMK <--
        )
    }

    fun CustomMangaInfo.toJson(): MangaJson {
        return MangaJson(
            id,
            title,
            author,
            artist,
            thumbnailUrl,
            description,
            genre,
            status,
            // KMK -->
            incognitoMode,
            // KMK <--
        )
    }
}
