package eu.kanade.domain.episode.interactor

import eu.kanade.domain.anime.interactor.GetExcludedScanlators
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.toSManga
import eu.kanade.domain.episode.model.copyFromSChapter
import eu.kanade.domain.episode.model.toSChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import exh.source.isEhBasedManga
import tachiyomi.data.episode.EpisodeSanitizer
import tachiyomi.domain.anime.model.Manga
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.ShouldUpdateDbEpisode
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.NoEpisodesException
import tachiyomi.domain.episode.model.toEpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.episode.service.EpisodeRecognition
import tachiyomi.source.local.isLocal
import java.lang.Long.max
import java.time.ZonedDateTime
import java.util.TreeSet

class SyncEpisodesWithSource(
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val episodeRepository: EpisodeRepository,
    private val shouldUpdateDbEpisode: ShouldUpdateDbEpisode,
    private val updateAnime: UpdateAnime,
    private val updateEpisode: UpdateEpisode,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getExcludedScanlators: GetExcludedScanlators,
) {

    /**
     * Method to synchronize db episodes with source ones
     *
     * @param rawSourceChapters the episodes from the source.
     * @param manga the manga the episodes belong to.
     * @param source the source the manga belongs to.
     * @return Newly added episodes
     */
    suspend fun await(
        rawSourceChapters: List<SEpisode>,
        manga: Manga,
        source: AnimeSource,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): List<Episode> {
        if (rawSourceChapters.isEmpty() && !source.isLocal()) {
            throw NoEpisodesException()
        }

        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()

        val sourceEpisodes = rawSourceChapters
            .distinctBy { it.url }
            .mapIndexed { i, sChapter ->
                Episode.create()
                    .copyFromSChapter(sChapter)
                    .copy(name = with(EpisodeSanitizer) { sChapter.name.sanitize(manga.title) })
                    .copy(mangaId = manga.id, sourceOrder = i.toLong())
            }

        val dbChapters = getEpisodesByAnimeId.await(manga.id)

        val newEpisodes = mutableListOf<Episode>()
        val updatedEpisodes = mutableListOf<Episode>()
        val removedChapters = dbChapters.filterNot { dbChapter ->
            sourceEpisodes.any { sourceChapter ->
                dbChapter.url == sourceChapter.url
            }
        }

        // Used to not set upload date of older episodes
        // to a higher value than newer episodes
        var maxSeenUploadDate = 0L

        for (sourceChapter in sourceEpisodes) {
            var chapter = sourceChapter

            // Update metadata from source if necessary.
            if (source is AnimeHttpSource) {
                val sChapter = chapter.toSChapter()
                source.prepareNewEpisode(sChapter, manga.toSManga())
                chapter = chapter.copyFromSChapter(sChapter)
            }

            // Recognize episode number for the episode.
            val chapterNumber = EpisodeRecognition.parseChapterNumber(
                manga.title,
                chapter.name,
                chapter.chapterNumber,
            )
            chapter = chapter.copy(chapterNumber = chapterNumber)

            val dbChapter = dbChapters.find { it.url == chapter.url }

            if (dbChapter == null) {
                val toAddChapter = if (chapter.dateUpload == 0L) {
                    val altDateUpload = if (maxSeenUploadDate == 0L) nowMillis else maxSeenUploadDate
                    chapter.copy(dateUpload = altDateUpload)
                } else {
                    maxSeenUploadDate = max(maxSeenUploadDate, sourceChapter.dateUpload)
                    chapter
                }
                newEpisodes.add(toAddChapter)
            } else {
                if (shouldUpdateDbEpisode.await(dbChapter, chapter)) {
                    val shouldRenameChapter = downloadProvider.isChapterDirNameChanged(dbChapter, chapter) &&
                        downloadManager.isChapterDownloaded(
                            dbChapter.name,
                            dbChapter.scanlator,
                            // SY -->
                            // manga.title,
                            manga.ogTitle,
                            // SY <--
                            manga.source,
                        )

                    if (shouldRenameChapter) {
                        downloadManager.renameChapter(source, manga, dbChapter, chapter)
                    }
                    var toChangeChapter = dbChapter.copy(
                        name = chapter.name,
                        chapterNumber = chapter.chapterNumber,
                        scanlator = chapter.scanlator,
                        sourceOrder = chapter.sourceOrder,
                    )
                    if (chapter.dateUpload != 0L) {
                        toChangeChapter = toChangeChapter.copy(dateUpload = chapter.dateUpload)
                    }
                    updatedEpisodes.add(toChangeChapter)
                }
            }
        }

        // Return if there's nothing to add, delete, or update to avoid unnecessary db transactions.
        if (newEpisodes.isEmpty() && removedChapters.isEmpty() && updatedEpisodes.isEmpty()) {
            if (manualFetch || manga.fetchInterval == 0 || manga.nextUpdate < fetchWindow.first) {
                updateAnime.awaitUpdateFetchInterval(
                    manga,
                    now,
                    fetchWindow,
                )
            }
            return emptyList()
        }

        val reAdded = mutableListOf<Episode>()

        val deletedChapterNumbers = TreeSet<Double>()
        val deletedReadChapterNumbers = TreeSet<Double>()
        val deletedBookmarkedChapterNumbers = TreeSet<Double>()

        removedChapters.forEach { chapter ->
            if (chapter.read) deletedReadChapterNumbers.add(chapter.chapterNumber)
            if (chapter.bookmark) deletedBookmarkedChapterNumbers.add(chapter.chapterNumber)
            deletedChapterNumbers.add(chapter.chapterNumber)
        }

        val deletedChapterNumberDateFetchMap = removedChapters.sortedByDescending { it.dateFetch }
            .associate { it.chapterNumber to it.dateFetch }

        // Date fetch is set in such a way that the upper ones will have bigger value than the lower ones
        // Sources MUST return the episodes from most to less recent, which is common.
        var itemCount = newEpisodes.size
        var updatedToAdd = newEpisodes.map { toAddItem ->
            var chapter = toAddItem.copy(dateFetch = nowMillis + itemCount--)

            if (!chapter.isRecognizedNumber || chapter.chapterNumber !in deletedChapterNumbers) return@map chapter

            chapter = chapter.copy(
                read = chapter.chapterNumber in deletedReadChapterNumbers,
                bookmark = chapter.chapterNumber in deletedBookmarkedChapterNumbers,
            )

            // Try to to use the fetch date of the original entry to not pollute 'Updates' tab
            deletedChapterNumberDateFetchMap[chapter.chapterNumber]?.let {
                chapter = chapter.copy(dateFetch = it)
            }

            reAdded.add(chapter)

            chapter
        }

        // --> EXH (carry over reading progress)
        if (manga.isEhBasedManga()) {
            val finalAdded = updatedToAdd.subtract(reAdded)
            if (finalAdded.isNotEmpty()) {
                val max = dbChapters.maxOfOrNull { it.lastPageRead }
                if (max != null && max > 0) {
                    updatedToAdd = updatedToAdd.map {
                        if (it !in reAdded) {
                            it.copy(lastPageRead = max)
                        } else {
                            it
                        }
                    }
                }
            }
        }
        // <-- EXH

        if (removedChapters.isNotEmpty()) {
            val toDeleteIds = removedChapters.map { it.id }
            episodeRepository.removeChaptersWithIds(toDeleteIds)
        }

        if (updatedToAdd.isNotEmpty()) {
            updatedToAdd = episodeRepository.addAll(updatedToAdd)
        }

        if (updatedEpisodes.isNotEmpty()) {
            val chapterUpdates = updatedEpisodes.map { it.toEpisodeUpdate() }
            updateEpisode.awaitAll(chapterUpdates)
        }
        updateAnime.awaitUpdateFetchInterval(manga, now, fetchWindow)

        // Set this manga as updated since episodes were changed
        // Note that last_update actually represents last time the episode list changed at all
        updateAnime.awaitUpdateLastUpdate(manga.id)

        val reAddedUrls = reAdded.map { it.url }.toHashSet()

        val excludedScanlators = getExcludedScanlators.await(manga.id).toHashSet()

        return updatedToAdd.filterNot {
            it.url in reAddedUrls || it.scanlator in excludedScanlators
        }
    }
}
