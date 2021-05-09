package exh.md.handlers

import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import exh.log.xLogE
import exh.md.handlers.serializers.AuthorResponseList
import exh.md.handlers.serializers.ChapterResponse
import exh.md.handlers.serializers.MangaResponse
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.metadata.metadata.base.insertFlatMetadata
import exh.util.dropEmpty
import exh.util.executeOnIO
import exh.util.floor
import okhttp3.OkHttpClient
import okhttp3.Response
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.Locale

class ApiMangaParser(val client: OkHttpClient, private val lang: String) {
    val db: DatabaseHelper by injectLazy()

    val metaClass = MangaDexSearchMetadata::class

    /**
     * Use reflection to create a new instance of metadata
     */
    private fun newMetaInstance() = metaClass.constructors.find {
        it.parameters.isEmpty()
    }?.call()
        ?: error("Could not find no-args constructor for meta class: ${metaClass.qualifiedName}!")

    suspend fun parseToManga(manga: MangaInfo, input: Response, coverUrls: List<String>, sourceId: Long): MangaInfo {
        return parseToManga(manga, input.parseAs<MangaResponse>(MdUtil.jsonParser), coverUrls, sourceId)
    }

    suspend fun parseToManga(manga: MangaInfo, input: MangaResponse, coverUrls: List<String>, sourceId: Long): MangaInfo {
        val mangaId = db.getManga(manga.key, sourceId).executeOnIO()?.id
        val metadata = if (mangaId != null) {
            val flatMetadata = db.getFlatMetadataForManga(mangaId).executeOnIO()
            flatMetadata?.raise(metaClass) ?: newMetaInstance()
        } else newMetaInstance()

        parseIntoMetadata(metadata, input, coverUrls)
        if (mangaId != null) {
            metadata.mangaId = mangaId
            db.insertFlatMetadata(metadata.flatten())
        }

        return metadata.createMangaInfo(manga)
    }

    /**
     * Parse the manga details json into metadata object
     */
    suspend fun parseIntoMetadata(metadata: MangaDexSearchMetadata, input: Response, coverUrls: List<String>) {
        parseIntoMetadata(metadata, input.parseAs<MangaResponse>(MdUtil.jsonParser), coverUrls)
    }

    suspend fun parseIntoMetadata(metadata: MangaDexSearchMetadata, networkApiManga: MangaResponse, coverUrls: List<String>) {
        with(metadata) {
            try {
                val networkManga = networkApiManga.data.attributes
                mdUuid = networkApiManga.data.id
                title = MdUtil.cleanString(networkManga.title[lang] ?: networkManga.title["en"]!!)
                altTitles = networkManga.altTitles.mapNotNull { it[lang] }
                cover =
                    if (coverUrls.isNotEmpty()) {
                        coverUrls.last()
                    } else {
                        null
                        // networkManga.mainCover
                    }

                description = MdUtil.cleanDescription(networkManga.description[lang] ?: networkManga.description["en"]!!)

                val authorIds = networkApiManga.relationships
                    .filter { it.type.equals("author", true) }
                    .map { it.id }
                    .toSet()
                val artistIds = networkApiManga.relationships
                    .filter { it.type.equals("artist", true) }
                    .map { it.id }
                    .toSet()

                // get author/artist map ignore if they error
                val authorMap = runCatching {
                    val ids = (authorIds + artistIds).joinToString("&ids[]=", "?ids[]=")
                    val response = client.newCall(GET("${MdUtil.authorUrl}$ids")).await()
                        .parseAs<AuthorResponseList>()
                    response.results.map {
                        it.data.id to MdUtil.cleanString(it.data.attributes.name)
                    }.toMap()
                }.getOrNull() ?: emptyMap()

                authors = authorIds.mapNotNull { authorMap[it] }.dropEmpty()
                artists = artistIds.mapNotNull { authorMap[it] }.dropEmpty()

                langFlag = networkManga.originalLanguage
                val lastChapter = networkManga.lastChapter.toFloatOrNull()
                lastChapterNumber = lastChapter?.floor()

                /*networkManga.rating?.let {
                    manga.rating = it.bayesian ?: it.mean
                    manga.users = it.users
                }*/

                networkManga.links?.let { links ->
                    links["al"]?.let { anilistId = it }
                    links["kt"]?.let { kitsuId = it }
                    links["mal"]?.let { myAnimeListId = it }
                    links["mu"]?.let { mangaUpdatesId = it }
                    links["ap"]?.let { animePlanetId = it }
                }

                if (kitsuId?.toIntOrNull() != null) {
                    cover = "https://media.kitsu.io/manga/poster_images/$kitsuId/large.jpg"
                }
                if (cover == null && !myAnimeListId.isNullOrEmpty()) {
                    cover = "https://coverapi.orell.dev/api/v1/mal/manga/$myAnimeListId/cover"
                }
                if (cover == null) {
                    cover = "https://i.imgur.com/6TrIues.jpg"
                }
                

                // val filteredChapters = filterChapterForChecking(networkApiManga)

                val tempStatus = parseStatus(networkManga.status ?: "")
                val publishedOrCancelled =
                    tempStatus == SManga.PUBLICATION_COMPLETE || tempStatus == SManga.CANCELLED
                /*if (publishedOrCancelled && isMangaCompleted(networkApiManga, filteredChapters)) {
                    manga.status = SManga.COMPLETED
                    manga.missing_chapters = null
                } else {*/
                status = tempStatus
                // }

                // things that will go with the genre tags but aren't actually genre
                val nonGenres = listOfNotNull(
                    networkManga.publicationDemographic?.let { RaisedTag("Demographic", it.capitalize(Locale.US), MangaDexSearchMetadata.TAG_TYPE_DEFAULT) },
                    networkManga.contentRating?.let { RaisedTag("Content Rating", it.capitalize(Locale.US), MangaDexSearchMetadata.TAG_TYPE_DEFAULT) },
                )

                val genres = nonGenres + networkManga.tags
                    .mapNotNull { dexTag ->
                        dexTag.attributes.name[lang] ?: dexTag.attributes.name["en"]
                    }.map {
                        RaisedTag("Tags", it, MangaDexSearchMetadata.TAG_TYPE_DEFAULT)
                    }

                if (tags.isNotEmpty()) tags.clear()
                tags += genres
            } catch (e: Exception) {
                xLogE("Parse into metadata error", e)
                throw e
            }
        }
    }

    /**
     * If chapter title is oneshot or a chapter exists which matches the last chapter in the required language
     * return manga is complete
     */
    /*private fun isMangaCompleted(
        serializer: ApiMangaSerializer,
        filteredChapters: List<ChapterSerializer>
    ): Boolean {
        if (filteredChapters.isEmpty() || serializer.data.manga.lastChapter.isNullOrEmpty()) {
            return false
        }
        val finalChapterNumber = serializer.data.manga.lastChapter!!
        if (MdUtil.validOneShotFinalChapters.contains(finalChapterNumber)) {
            filteredChapters.firstOrNull()?.let {
                if (isOneShot(it, finalChapterNumber)) {
                    return true
                }
            }
        }
        val removeOneshots = filteredChapters.asSequence()
            .map { it.chapter!!.toDoubleOrNull() }
            .filter { it != null }
            .map { floor(it!!).toInt() }
            .filter { it != 0 }
            .toList().distinctBy { it }
        return removeOneshots.toList().size == floor(finalChapterNumber.toDouble()).toInt()
    }*/

    /* private fun filterChapterForChecking(serializer: ApiMangaSerializer): List<ChapterSerializer> {
         serializer.data.chapters ?: return emptyList()
         return serializer.data.chapters.asSequence()
             .filter { langs.contains(it.language) }
             .filter {
                 it.chapter?.let { chapterNumber ->
                     if (chapterNumber.toDoubleOrNull() == null) {
                         return@filter false
                     }
                     return@filter true
                 }
                 return@filter false
             }.toList()
     }*/

    /*private fun isOneShot(chapter: ChapterSerializer, finalChapterNumber: String): Boolean {
        return chapter.title.equals("oneshot", true) ||
            ((chapter.chapter.isNullOrEmpty() || chapter.chapter == "0") && MdUtil.validOneShotFinalChapters.contains(finalChapterNumber))
    }*/

    private fun parseStatus(status: String) = when (status) {
        "ongoing" -> SManga.ONGOING
        "complete" -> SManga.PUBLICATION_COMPLETE
        "cancelled" -> SManga.CANCELLED
        "hiatus" -> SManga.HIATUS
        else -> SManga.UNKNOWN
    }

    /**
     * Parse for the random manga id from the [MdUtil.randMangaPage] response.
     */
    fun randomMangaIdParse(response: Response): String {
        return response.parseAs<MangaResponse>(MdUtil.jsonParser).data.id
    }

    fun chapterListParse(chapterListResponse: List<ChapterResponse>, groupMap: Map<String, String>): List<ChapterInfo> {
        val now = Date().time

        return chapterListResponse.asSequence()
            .map {
                mapChapter(it, groupMap)
            }.filter {
                it.dateUpload <= now && "MangaPlus" != it.scanlator
            }.toList()
    }

    fun chapterParseForMangaId(response: Response): String {
        try {
            return response.parseAs<ChapterResponse>(MdUtil.jsonParser)
                .relationships.firstOrNull { it.type.equals("manga", true) }?.id ?: throw Exception("Not found")
        } catch (e: Exception) {
            XLog.e(e)
            throw e
        }
    }

    fun StringBuilder.appends(string: String) = append("$string ")

    private fun mapChapter(
        networkChapter: ChapterResponse,
        groups: Map<String, String>,
    ): ChapterInfo {
        val attributes = networkChapter.data.attributes
        val key = MdUtil.chapterSuffix + networkChapter.data.id
        val chapterName = StringBuilder()
        // Build chapter name

        if (attributes.volume != null) {
            val vol = "Vol." + attributes.volume
            chapterName.appends(vol)
            // todo
            // chapter.vol = vol
        }

        if (attributes.chapter.isNullOrBlank().not()) {
            val chp = "Ch.${attributes.chapter}"
            chapterName.appends(chp)
            // chapter.chapter_txt = chp
        }

        if (!attributes.title.isNullOrBlank()) {
            if (chapterName.isNotEmpty()) {
                chapterName.appends("-")
            }
            chapterName.append(attributes.title)
        }

        // if volume, chapter and title is empty its a oneshot
        if (chapterName.isEmpty()) {
            chapterName.append("Oneshot")
        }
        /*if ((status == 2 || status == 3)) {
            if (finalChapterNumber != null) {
                if ((isOneShot(networkChapter, finalChapterNumber) && totalChapterCount == 1) ||
                    networkChapter.chapter == finalChapterNumber && finalChapterNumber.toIntOrNull() != 0
                ) {
                    chapterName.add("[END]")
                }
            }
        }*/

        val name = MdUtil.cleanString(chapterName.toString())
        // Convert from unix time
        val dateUpload = MdUtil.parseDate(attributes.publishAt)

        val scanlatorName = networkChapter.relationships.filter { it.type == "scanlation_group" }.mapNotNull { groups[it.id] }.toSet()

        val scanlator = MdUtil.cleanString(MdUtil.getScanlatorString(scanlatorName))

        // chapter.mangadex_chapter_id = MdUtil.getChapterId(chapter.url)

        // chapter.language = MdLang.fromIsoCode(attributes.translatedLanguage)?.prettyPrint ?: ""

        return ChapterInfo(
            key = key,
            name = name,
            scanlator = scanlator,
            dateUpload = dateUpload,
        )
    }
}
