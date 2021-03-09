package exh.md.handlers

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import exh.log.xLogE
import exh.md.handlers.serializers.ApiChapterSerializer
import exh.md.handlers.serializers.ApiMangaSerializer
import exh.md.handlers.serializers.ChapterSerializer
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.metadata.metadata.base.getFlatMetadataForManga
import exh.metadata.metadata.base.insertFlatMetadata
import exh.metadata.metadata.base.insertFlatMetadataAsync
import exh.util.executeOnIO
import exh.util.floor
import exh.util.nullIfZero
import okhttp3.Response
import rx.Completable
import rx.Single
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ApiMangaParser(private val lang: String) {
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
    fun parseToManga(manga: SManga, input: Response, coverUrls: List<String>): Completable {
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
            parseIntoMetadata(it, input, coverUrls)
            it.copyTo(manga)
            it
        }.flatMapCompletable {
            if (mangaId != null) {
                it.mangaId = mangaId
                db.insertFlatMetadata(it.flatten())
            } else Completable.complete()
        }
    }

    suspend fun parseToManga(manga: MangaInfo, input: Response, coverUrls: List<String>, sourceId: Long): MangaInfo {
        val mangaId = db.getManga(manga.key, sourceId).executeOnIO()?.id
        val metadata = if (mangaId != null) {
            val flatMetadata = db.getFlatMetadataForManga(mangaId).executeOnIO()
            flatMetadata?.raise(metaClass) ?: newMetaInstance()
        } else newMetaInstance()

        parseInfoIntoMetadata(metadata, input, coverUrls)
        if (mangaId != null) {
            metadata.mangaId = mangaId
            db.insertFlatMetadataAsync(metadata.flatten()).await()
        }

        return metadata.createMangaInfo(manga)
    }

    fun parseInfoIntoMetadata(metadata: MangaDexSearchMetadata, input: Response, coverUrls: List<String>) = parseIntoMetadata(metadata, input, coverUrls)

    fun parseIntoMetadata(metadata: MangaDexSearchMetadata, input: Response, coverUrls: List<String>) {
        with(metadata) {
            try {
                val networkApiManga = input.parseAs<ApiMangaSerializer>(MdUtil.jsonParser)
                val networkManga = networkApiManga.data.manga
                mdId = MdUtil.getMangaId(input.request.url.toString())
                mdUrl = input.request.url.toString()
                title = MdUtil.cleanString(networkManga.title)
                thumbnail_url = if (coverUrls.isNotEmpty()) {
                    coverUrls.last()
                } else {
                    networkManga.mainCover
                }
                description = MdUtil.cleanDescription(networkManga.description)
                author = MdUtil.cleanString(networkManga.author.joinToString())
                artist = MdUtil.cleanString(networkManga.artist.joinToString())
                lang_flag = networkManga.publication?.language
                last_chapter_number = networkManga.lastChapter?.toFloatOrNull()?.floor()

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

                val tempStatus = parseStatus(networkManga.publication!!.status)
                val publishedOrCancelled =
                    tempStatus == SManga.PUBLICATION_COMPLETE || tempStatus == SManga.CANCELLED
                if (publishedOrCancelled && isMangaCompleted(networkApiManga, filteredChapters)) {
                    status = SManga.COMPLETED
                    missing_chapters = null
                    maxChapterNumber = networkApiManga.data.manga.lastChapter?.toDoubleOrNull()?.floor()
                } else {
                    status = tempStatus
                }

                val genres =
                    networkManga.tags.mapNotNull { FilterHandler.allTypes[it.toString()] }
                        .toMutableList()

                networkManga.publication.demographic?.let { demographicInt ->
                    val demographic = FilterHandler.demographics().firstOrNull { it.id.toInt() == demographicInt }

                    if (demographic != null) {
                        genres.add(0, demographic.name)
                    }
                }

                if (networkManga.isHentai) {
                    genres.add("Hentai")
                }

                if (tags.isNotEmpty()) tags.clear()
                tags += genres.map { RaisedTag(null, it, MangaDexSearchMetadata.TAG_TYPE_DEFAULT) }
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
    private fun isMangaCompleted(
        serializer: ApiMangaSerializer,
        filteredChapters: List<ChapterSerializer>
    ): Boolean {
        val finalChapterNumber = serializer.data.manga.lastChapter
        if (filteredChapters.isEmpty() || finalChapterNumber.isNullOrEmpty()) {
            return false
        }
        // just to fix the stupid lint
        if (MdUtil.validOneShotFinalChapters.contains(finalChapterNumber)) {
            filteredChapters.firstOrNull()?.let {
                if (isOneShot(it, finalChapterNumber)) {
                    return true
                }
            }
        }
        val removeOneshots = filteredChapters.asSequence()
            .map { it.chapter?.toDoubleOrNull()?.floor()?.nullIfZero() }
            .filterNotNull()
            .toList().distinctBy { it }
        return removeOneshots.toList().size == finalChapterNumber.toDouble().floor()
    }

    private fun filterChapterForChecking(serializer: ApiMangaSerializer): List<ChapterSerializer> {
        return serializer.data.chapters.asSequence()
            .filter { lang == it.language }
            .filter {
                it.chapter?.let { chapterNumber ->
                    if (chapterNumber.toDoubleOrNull() == null) {
                        return@filter false
                    }
                    return@filter true
                }
                return@filter false
            }.toList()
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

    fun chapterListParse(response: Response): List<ChapterInfo> {
        return chapterListParse(response.parseAs<ApiMangaSerializer>(MdUtil.jsonParser))
    }

    fun chapterListParse(networkApiManga: ApiMangaSerializer): List<ChapterInfo> {
        val now = System.currentTimeMillis()
        val networkManga = networkApiManga.data.manga
        val networkChapters = networkApiManga.data.chapters
        val groups = networkApiManga.data.groups.mapNotNull {
            if (it.name == null) {
                null
            } else {
                it.id to it.name
            }
        }.toMap()

        val status = networkManga.publication!!.status

        val finalChapterNumber = networkManga.lastChapter

        // Skip chapters that don't match the desired language, or are future releases

        val chapLang = MdLang.values().firstOrNull { lang == it.dexLang }
        return networkChapters.asSequence()
            .filter { lang == it.language && (it.timestamp * 1000) <= now }
            .map { mapChapter(it, finalChapterNumber, status, chapLang, networkChapters.size, groups) }.toList()
    }

    fun chapterParseForMangaId(response: Response): Int {
        try {
            return response.parseAs<ApiChapterSerializer>().data.mangaId
        } catch (e: Exception) {
            xLogE("Parse for manga id error", e)
            throw e
        }
    }

    private fun mapChapter(
        networkChapter: ChapterSerializer,
        finalChapterNumber: String?,
        status: Int,
        chapLang: MdLang?,
        totalChapterCount: Int,
        groups: Map<Long, String>
    ): ChapterInfo {
        val key = MdUtil.oldApiChapter + networkChapter.id

        // Build chapter name
        val chapterName = mutableListOf<String>()

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
            if (finalChapterNumber != null) {
                if ((isOneShot(networkChapter, finalChapterNumber) && totalChapterCount == 1) ||
                    networkChapter.chapter == finalChapterNumber && finalChapterNumber.toIntOrNull() != 0
                ) {
                    chapterName.add("[END]")
                }
            }
        }

        val name = MdUtil.cleanString(chapterName.joinToString(" "))
        // Convert from unix time
        val dateUpload = networkChapter.timestamp * 1000
        val scanlatorName = mutableSetOf<String>()

        networkChapter.groups.mapNotNull { groups[it] }.forEach { scanlatorName.add(it) }

        val scanlator = MdUtil.cleanString(MdUtil.getScanlatorString(scanlatorName))

        // val mangadexChapterId = MdUtil.getChapterId(chapter.url)

        // val language = chapLang?.name

        return ChapterInfo(
            key = key,
            name = name,
            dateUpload = dateUpload,
            scanlator = scanlator
        )
    }
}
