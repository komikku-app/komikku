package exh.md.handlers

import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import exh.md.handlers.serializers.ApiMangaSerializer
import exh.md.handlers.serializers.ChapterSerializer
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.metadata.metadata.base.insertFlatMetadata
import exh.util.floor
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import rx.Completable
import rx.Single
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

class ApiMangaParser(private val langs: List<String>) {
    val db: DatabaseHelper get() = Injekt.get()

    val metaClass = MangaDexSearchMetadata::class
    /**
     * Use reflection to create a new instance of metadata
     */
    private fun newMetaInstance() = metaClass.constructors.find {
        it.parameters.isEmpty()
    }?.call()
        ?: error("Could not find no-args constructor for meta class: ${metaClass.qualifiedName}!")
    /**
     * Parses metadata from the input and then copies it into the manga
     *
     * Will also save the metadata to the DB if possible
     */
    fun parseToManga(manga: SManga, input: Response, forceLatestCover: Boolean): Completable {
        val mangaId = (manga as? Manga)?.id
        val metaObservable = if (mangaId != null) {
            // We have to use fromCallable because StorIO messes up the thread scheduling if we use their rx functions
            Single.fromCallable {
                db.getFlatMetadataForManga(mangaId).executeAsBlocking()
            }.map {
                it?.raise(metaClass) ?: newMetaInstance()
            }
        } else {
            Single.just(newMetaInstance())
        }

        return metaObservable.map {
            parseIntoMetadata(it, input, forceLatestCover)
            it.copyTo(manga)
            it
        }.flatMapCompletable {
            if (mangaId != null) {
                it.mangaId = mangaId
                db.insertFlatMetadata(it.flatten())
            } else Completable.complete()
        }
    }

    fun parseIntoMetadata(metadata: MangaDexSearchMetadata, input: Response, forceLatestCover: Boolean) {
        with(metadata) {
            try {
                val networkApiManga = MdUtil.jsonParser.decodeFromString(ApiMangaSerializer.serializer(), input.body!!.string())
                val networkManga = networkApiManga.manga
                mdId = MdUtil.getMangaId(input.request.url.toString())
                mdUrl = input.request.url.toString()
                title = MdUtil.cleanString(networkManga.title)
                val coverList = networkManga.covers
                thumbnail_url = MdUtil.cdnUrl +
                    if (forceLatestCover && coverList.isNotEmpty()) {
                        coverList.last()
                    } else {
                        MdUtil.removeTimeParamUrl(networkManga.cover_url)
                    }
                description = MdUtil.cleanDescription(networkManga.description)
                author = MdUtil.cleanString(networkManga.author)
                artist = MdUtil.cleanString(networkManga.artist)
                lang_flag = networkManga.lang_flag
                last_chapter_number = networkManga.last_chapter?.toFloatOrNull()?.floor()

                networkManga.rating?.let {
                    rating = it.bayesian ?: it.mean
                    users = it.users
                }
                networkManga.links?.let { links ->
                    links.al?.let { anilist_id = it }
                    links.kt?.let { kitsu_id = it }
                    links.mal?.let { my_anime_list_id = it }
                    links.mu?.let { manga_updates_id = it }
                    links.ap?.let { anime_planet_id = it }
                }
                val filteredChapters = filterChapterForChecking(networkApiManga)

                val tempStatus = parseStatus(networkManga.status)
                val publishedOrCancelled =
                    tempStatus == SManga.PUBLICATION_COMPLETE || tempStatus == SManga.CANCELLED
                if (publishedOrCancelled && isMangaCompleted(networkApiManga, filteredChapters)) {
                    status = SManga.COMPLETED
                    missing_chapters = null
                } else {
                    status = tempStatus
                }

                val demographic = FilterHandler.demographics().filter { it.id == networkManga.demographic }.firstOrNull()

                val genres =
                    networkManga.genres.mapNotNull { FilterHandler.allTypes[it.toString()] }
                        .toMutableList()

                if (demographic != null) {
                    genres.add(0, demographic.name)
                }

                if (networkManga.hentai == 1) {
                    genres.add("Hentai")
                }

                if (tags.size != 0) tags.clear()
                tags += genres.map { RaisedTag(null, it, MangaDexSearchMetadata.TAG_TYPE_DEFAULT) }
            } catch (e: Exception) {
                XLog.e(e)
                throw e
            }
        }
    }

    /**
     * If chapter title is oneshot or a chapter exists which matches the last chapter in the required language
     * return manga is complete
     */
    private fun isMangaCompleted(
        serializer: ApiMangaSerializer,
        filteredChapters: List<Map.Entry<String, ChapterSerializer>>
    ): Boolean {
        if (filteredChapters.isEmpty() || serializer.manga.last_chapter.isNullOrEmpty()) {
            return false
        }
        // just to fix the stupid lint
        val lastMangaChapter: String? = serializer.manga.last_chapter
        val finalChapterNumber = lastMangaChapter!!
        if (MdUtil.validOneShotFinalChapters.contains(finalChapterNumber)) {
            filteredChapters.firstOrNull()?.let {
                if (isOneShot(it.value, finalChapterNumber)) {
                    return true
                }
            }
        }
        val removeOneshots = filteredChapters.filter { !it.value.chapter.isNullOrBlank() }
        return removeOneshots.size.toString() == finalChapterNumber.toDouble().floor().toString()
    }

    private fun filterChapterForChecking(serializer: ApiMangaSerializer): List<Map.Entry<String, ChapterSerializer>> {
        serializer.chapter ?: return emptyList()
        return serializer.chapter.entries
            .filter { langs.contains(it.value.lang_code) }
            .filter {
                it.value.chapter?.let { chapterNumber ->
                    if (chapterNumber.toIntOrNull() == null) {
                        return@filter false
                    }
                    return@filter true
                }
                return@filter false
            }.distinctBy { it.value.chapter }
    }

    private fun isOneShot(chapter: ChapterSerializer, finalChapterNumber: String): Boolean {
        return chapter.title.equals("oneshot", true) ||
            ((chapter.chapter.isNullOrEmpty() || chapter.chapter == "0") && MdUtil.validOneShotFinalChapters.contains(finalChapterNumber))
    }

    private fun parseStatus(status: Int) = when (status) {
        1 -> SManga.ONGOING
        2 -> SManga.PUBLICATION_COMPLETE
        3 -> SManga.CANCELLED
        4 -> SManga.HIATUS
        else -> SManga.UNKNOWN
    }

    /**
     * Parse for the random manga id from the [MdUtil.randMangaPage] response.
     */
    fun randomMangaIdParse(response: Response): String {
        val randMangaUrl = response.asJsoup()
            .select("link[rel=canonical]")
            .attr("href")
        return MdUtil.getMangaId(randMangaUrl)
    }

    fun chapterListParse(response: Response): List<SChapter> {
        return chapterListParse(response.body!!.string())
    }

    fun chapterListParse(jsonData: String): List<SChapter> {
        val now = Date().time
        val networkApiManga =
            MdUtil.jsonParser.decodeFromString(ApiMangaSerializer.serializer(), jsonData)
        val networkManga = networkApiManga.manga
        val networkChapters = networkApiManga.chapter
        if (networkChapters.isNullOrEmpty()) {
            return listOf()
        }
        val status = networkManga.status

        val finalChapterNumber = networkManga.last_chapter!!

        val chapters = mutableListOf<SChapter>()

        // Skip chapters that don't match the desired language, or are future releases

        val chapLangs = MdLang.values().filter { langs.contains(it.dexLang) }
        networkChapters.filter { langs.contains(it.value.lang_code) && (it.value.timestamp * 1000) <= now }
            .mapTo(chapters) { mapChapter(it.key, it.value, finalChapterNumber, status, chapLangs, networkChapters.size) }

        return chapters
    }

    fun chapterParseForMangaId(response: Response): Int {
        try {
            if (response.code != 200) throw Exception("HTTP error ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) {
                throw Exception("Null Response")
            }

            val jsonObject = Json.decodeFromString<JsonObject>(body)
            return jsonObject["manga_id"]?.jsonPrimitive?.intOrNull ?: throw Exception("No manga associated with chapter")
        } catch (e: Exception) {
            XLog.e(e)
            throw e
        }
    }

    private fun mapChapter(
        chapterId: String,
        networkChapter: ChapterSerializer,
        finalChapterNumber: String,
        status: Int,
        chapLangs: List<MdLang>,
        totalChapterCount: Int
    ): SChapter {
        val chapter = SChapter.create()
        chapter.url = MdUtil.apiChapter + chapterId
        val chapterName = mutableListOf<String>()
        // Build chapter name

        if (!networkChapter.volume.isNullOrBlank()) {
            val vol = "Vol." + networkChapter.volume
            chapterName.add(vol)
            // todo
            // chapter.vol = vol
        }

        if (!networkChapter.chapter.isNullOrBlank()) {
            val chp = "Ch." + networkChapter.chapter
            chapterName.add(chp)
            // chapter.chapter_txt = chp
        }
        if (!networkChapter.title.isNullOrBlank()) {
            if (chapterName.isNotEmpty()) {
                chapterName.add("-")
            }
            // todo
            chapterName.add(networkChapter.title)
            // chapter.chapter_title = MdUtil.cleanString(networkChapter.title)
        }

        // if volume, chapter and title is empty its a oneshot
        if (chapterName.isEmpty()) {
            chapterName.add("Oneshot")
        }
        if ((status == 2 || status == 3)) {
            if ((isOneShot(networkChapter, finalChapterNumber) && totalChapterCount == 1) ||
                networkChapter.chapter == finalChapterNumber && finalChapterNumber.toIntOrNull() != 0
            ) {
                chapterName.add("[END]")
            }
        }

        chapter.name = MdUtil.cleanString(chapterName.joinToString(" "))
        // Convert from unix time
        chapter.date_upload = networkChapter.timestamp * 1000
        val scanlatorName = mutableSetOf<String>()

        networkChapter.group_name?.let {
            scanlatorName.add(it)
        }
        networkChapter.group_name_2?.let {
            scanlatorName.add(it)
        }
        networkChapter.group_name_3?.let {
            scanlatorName.add(it)
        }

        chapter.scanlator = MdUtil.cleanString(MdUtil.getScanlatorString(scanlatorName))

        // chapter.mangadex_chapter_id = MdUtil.getChapterId(chapter.url)

        // chapter.language = chapLangs.firstOrNull { it.dexLang == networkChapter.lang_code }?.name

        return chapter
    }
}
