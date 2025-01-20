package exh.md.handlers

import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.SAnime
import exh.log.xLogE
import exh.md.dto.ChapterDataDto
import exh.md.dto.ChapterDto
import exh.md.dto.MangaDto
import exh.md.dto.StatisticsMangaDto
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import exh.md.utils.asMdMap
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.util.capitalize
import exh.util.floor
import exh.util.nullIfEmpty
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetFlatMetadataById
import tachiyomi.domain.anime.interactor.InsertFlatMetadata
import uy.kohesive.injekt.injectLazy
import java.util.Locale

class ApiMangaParser(
    private val lang: String,
) {
    private val getAnime: GetAnime by injectLazy()
    private val insertFlatMetadata: InsertFlatMetadata by injectLazy()
    private val getFlatMetadataById: GetFlatMetadataById by injectLazy()

    val metaClass = MangaDexSearchMetadata::class

    /**
     * Use reflection to create a new instance of metadata
     */
    private fun newMetaInstance() = MangaDexSearchMetadata()

    suspend fun parseToManga(
        manga: SAnime,
        sourceId: Long,
        input: MangaDto,
        simpleChapters: List<String>,
        statistics: StatisticsMangaDto?,
        coverFileName: String?,
        coverQuality: String,
        altTitlesInDesc: Boolean,
    ): SAnime {
        val mangaId = getAnime.await(manga.url, sourceId)?.id
        val metadata = if (mangaId != null) {
            val flatMetadata = getFlatMetadataById.await(mangaId)
            flatMetadata?.raise(metaClass) ?: newMetaInstance()
        } else {
            newMetaInstance()
        }

        parseIntoMetadata(metadata, input, simpleChapters, statistics, coverFileName, coverQuality, altTitlesInDesc)
        if (mangaId != null) {
            metadata.mangaId = mangaId
            insertFlatMetadata.await(metadata.flatten())
        }

        return metadata.createMangaInfo(manga)
    }

    fun parseIntoMetadata(
        metadata: MangaDexSearchMetadata,
        mangaDto: MangaDto,
        simpleChapters: List<String>,
        statistics: StatisticsMangaDto?,
        coverFileName: String?,
        coverQuality: String,
        altTitlesInDesc: Boolean,
    ) {
        with(metadata) {
            try {
                val mangaAttributesDto = mangaDto.data.attributes
                mdUuid = mangaDto.data.id
                title = MdUtil.getTitleFromManga(mangaAttributesDto, lang)
                altTitles = mangaAttributesDto.altTitles.mapNotNull { it[lang] }.nullIfEmpty()

                val mangaRelationshipsDto = mangaDto.data.relationships
                cover = if (!coverFileName.isNullOrEmpty()) {
                    MdUtil.cdnCoverUrl(mangaDto.data.id, "$coverFileName$coverQuality")
                } else {
                    mangaRelationshipsDto
                        .firstOrNull { relationshipDto -> relationshipDto.type == MdConstants.Types.coverArt }
                        ?.attributes
                        ?.fileName
                        ?.let { coverFileName ->
                            MdUtil.cdnCoverUrl(mangaDto.data.id, "$coverFileName$coverQuality")
                        }
                }
                val rawDesc = MdUtil.getFromLangMap(
                    langMap = mangaAttributesDto.description.asMdMap(),
                    currentLang = lang,
                    originalLanguage = mangaAttributesDto.originalLanguage,
                ).orEmpty()

                description = MdUtil.cleanDescription(
                    if (altTitlesInDesc) MdUtil.addAltTitleToDesc(rawDesc, altTitles) else rawDesc,
                )

                authors = mangaRelationshipsDto.filter { relationshipDto ->
                    relationshipDto.type.equals(MdConstants.Types.author, true)
                }.mapNotNull { it.attributes?.name }.distinct()

                artists = mangaRelationshipsDto.filter { relationshipDto ->
                    relationshipDto.type.equals(MdConstants.Types.artist, true)
                }.mapNotNull { it.attributes?.name }.distinct()

                langFlag = mangaAttributesDto.originalLanguage
                val lastChapter = mangaAttributesDto.lastChapter?.toFloatOrNull()
                lastChapterNumber = lastChapter?.floor()

                statistics?.rating?.let {
                    rating = it.bayesian?.toFloat()
                    // manga.users = it.users
                }

                mangaAttributesDto.links?.asMdMap<String>()?.let { links ->
                    links["al"]?.let { anilistId = it }
                    links["kt"]?.let { kitsuId = it }
                    links["mal"]?.let { myAnimeListId = it }
                    links["mu"]?.let { mangaUpdatesId = it }
                    links["ap"]?.let { animePlanetId = it }
                }

                // val filteredChapters = filterChapterForChecking(networkApiManga)

                val tempStatus = parseStatus(mangaAttributesDto.status)
                val publishedOrCancelled = tempStatus == SAnime.PUBLISHING_FINISHED || tempStatus == SAnime.CANCELLED
                status = if (
                    mangaAttributesDto.lastChapter != null &&
                    publishedOrCancelled &&
                    mangaAttributesDto.lastChapter in simpleChapters
                ) {
                    SAnime.COMPLETED
                } else {
                    tempStatus
                }

                // things that will go with the genre tags but aren't actually genre
                val nonGenres = listOfNotNull(
                    mangaAttributesDto.publicationDemographic
                        ?.let {
                            RaisedTag("Demographic", it.capitalize(Locale.US), MangaDexSearchMetadata.TAG_TYPE_DEFAULT)
                        },
                    mangaAttributesDto.contentRating
                        ?.takeUnless { it == "safe" }
                        ?.let {
                            RaisedTag("Content Rating", it.capitalize(Locale.US), MangaDexSearchMetadata.TAG_TYPE_DEFAULT)
                        },
                )

                val genres = nonGenres + mangaAttributesDto.tags
                    .mapNotNull {
                        it.attributes.name[lang] ?: it.attributes.name["en"]
                    }
                    .map {
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

    /* private fun filterChapterForChecking(serializer: ApiMangaSerializer): List<ChapterSerializer> {
         serializer.data.episodes ?: return emptyList()
         return serializer.data.episodes.asSequence()
             .filter { langs.contains(it.language) }
             .filter {
                 it.episode?.let { chapterNumber ->
                     if (chapterNumber.toDoubleOrNull() == null) {
                         return@filter false
                     }
                     return@filter true
                 }
                 return@filter false
             }.toList()
     }*/

    /*private fun isOneShot(episode: ChapterSerializer, finalChapterNumber: String): Boolean {
        return episode.title.equals("oneshot", true) ||
            ((episode.episode.isNullOrEmpty() || episode.episode == "0") && MdUtil.validOneShotFinalChapters.contains(finalChapterNumber))
    }*/

    private fun parseStatus(status: String?) = when (status) {
        "ongoing" -> SAnime.ONGOING
        "completed" -> SAnime.PUBLISHING_FINISHED
        "cancelled" -> SAnime.CANCELLED
        "hiatus" -> SAnime.ON_HIATUS
        else -> SAnime.UNKNOWN
    }

    fun chapterListParse(chapterListResponse: List<ChapterDataDto>, groupMap: Map<String, String>): List<SEpisode> {
        val now = System.currentTimeMillis()
        return chapterListResponse
            .filterNot { MdUtil.parseDate(it.attributes.publishAt) > now && it.attributes.externalUrl == null }
            .map {
                mapChapter(it, groupMap)
            }
    }

    fun chapterParseForMangaId(chapterDto: ChapterDto): String? {
        return chapterDto.data.relationships.find { it.type.equals("manga", true) }?.id
    }

    fun StringBuilder.appends(string: String): StringBuilder = append("$string ")

    private fun mapChapter(
        networkChapter: ChapterDataDto,
        groups: Map<String, String>,
    ): SEpisode {
        val attributes = networkChapter.attributes
        val key = MdUtil.chapterSuffix + networkChapter.id
        val chapterName = StringBuilder()
        // Build episode name

        if (attributes.volume != null) {
            val vol = "Vol." + attributes.volume
            chapterName.appends(vol)
            // todo
            // episode.vol = vol
        }

        if (attributes.chapter.isNullOrBlank().not()) {
            val chp = "Ch.${attributes.chapter}"
            chapterName.appends(chp)
            // episode.chapter_txt = chp
        }

        if (!attributes.title.isNullOrBlank()) {
            if (chapterName.isNotEmpty()) {
                chapterName.appends("-")
            }
            chapterName.append(attributes.title)
        }

        // if volume, episode and title is empty its a oneshot
        if (chapterName.isEmpty()) {
            chapterName.append("Oneshot")
        }
        /*if ((status == 2 || status == 3)) {
            if (finalChapterNumber != null) {
                if ((isOneShot(networkChapter, finalChapterNumber) && totalChapterCount == 1) ||
                    networkChapter.episode == finalChapterNumber && finalChapterNumber.toIntOrNull() != 0
                ) {
                    chapterName.add("[END]")
                }
            }
        }*/

        val name = chapterName.toString()
        // Convert from unix time
        val dateUpload = MdUtil.parseDate(attributes.readableAt)

        val scanlatorName = networkChapter.relationships
            .filter {
                it.type == MdConstants.Types.scanlator
            }
            .mapNotNull { groups[it.id] }
            .map {
                if (it == "no group") {
                    "No Group"
                } else {
                    it
                }
            }
            .toSet()
            .ifEmpty { setOf("No Group") }

        val scanlator = MdUtil.getScanlatorString(scanlatorName)

        // episode.mangadex_chapter_id = MdUtil.getChapterId(episode.url)

        // episode.language = MdLang.fromIsoCode(attributes.translatedLanguage)?.prettyPrint ?: ""

        return SEpisode(
            url = key,
            name = name,
            scanlator = scanlator,
            date_upload = dateUpload,
        )
    }
}
