package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository

class SetAnimeEpisodeFlags(
    private val animeRepository: AnimeRepository,
) {

    suspend fun awaitSetDownloadedFilter(anime: Anime, flag: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = anime.id,
                chapterFlags = anime.chapterFlags.setFlag(flag, Anime.CHAPTER_DOWNLOADED_MASK),
            ),
        )
    }

    suspend fun awaitSetUnreadFilter(anime: Anime, flag: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = anime.id,
                chapterFlags = anime.chapterFlags.setFlag(flag, Anime.CHAPTER_UNREAD_MASK),
            ),
        )
    }

    suspend fun awaitSetBookmarkFilter(anime: Anime, flag: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = anime.id,
                chapterFlags = anime.chapterFlags.setFlag(flag, Anime.CHAPTER_BOOKMARKED_MASK),
            ),
        )
    }

    suspend fun awaitSetDisplayMode(anime: Anime, flag: Long): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = anime.id,
                chapterFlags = anime.chapterFlags.setFlag(flag, Anime.CHAPTER_DISPLAY_MASK),
            ),
        )
    }

    suspend fun awaitSetSortingModeOrFlipOrder(anime: Anime, flag: Long): Boolean {
        val newFlags = anime.chapterFlags.let {
            if (anime.sorting == flag) {
                // Just flip the order
                val orderFlag = if (anime.sortDescending()) {
                    Anime.CHAPTER_SORT_ASC
                } else {
                    Anime.CHAPTER_SORT_DESC
                }
                it.setFlag(orderFlag, Anime.CHAPTER_SORT_DIR_MASK)
            } else {
                // Set new flag with ascending order
                it
                    .setFlag(flag, Anime.CHAPTER_SORTING_MASK)
                    .setFlag(Anime.CHAPTER_SORT_ASC, Anime.CHAPTER_SORT_DIR_MASK)
            }
        }
        return animeRepository.update(
            AnimeUpdate(
                id = anime.id,
                chapterFlags = newFlags,
            ),
        )
    }

    suspend fun awaitSetAllFlags(
        mangaId: Long,
        unreadFilter: Long,
        downloadedFilter: Long,
        bookmarkedFilter: Long,
        sortingMode: Long,
        sortingDirection: Long,
        displayMode: Long,
    ): Boolean {
        return animeRepository.update(
            AnimeUpdate(
                id = mangaId,
                chapterFlags = 0L.setFlag(unreadFilter, Anime.CHAPTER_UNREAD_MASK)
                    .setFlag(downloadedFilter, Anime.CHAPTER_DOWNLOADED_MASK)
                    .setFlag(bookmarkedFilter, Anime.CHAPTER_BOOKMARKED_MASK)
                    .setFlag(sortingMode, Anime.CHAPTER_SORTING_MASK)
                    .setFlag(sortingDirection, Anime.CHAPTER_SORT_DIR_MASK)
                    .setFlag(displayMode, Anime.CHAPTER_DISPLAY_MASK),
            ),
        )
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
