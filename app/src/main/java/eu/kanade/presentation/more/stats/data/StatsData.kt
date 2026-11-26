package eu.kanade.presentation.more.stats.data

import tachiyomi.domain.readinglog.model.DayActivity
import tachiyomi.domain.readinglog.model.StreakInfo

sealed interface StatsData {

    data class Overview(
        val libraryMangaCount: Int,
        val completedMangaCount: Int,
        val totalReadDuration: Long,
    ) : StatsData

    data class Titles(
        val globalUpdateItemCount: Int,
        val startedMangaCount: Int,
        val localMangaCount: Int,
    ) : StatsData

    data class Chapters(
        val totalChapterCount: Int,
        val readChapterCount: Int,
        val downloadCount: Int,
    ) : StatsData

    data class Trackers(
        val trackedTitleCount: Int,
        val meanScore: Double,
        val trackerCount: Int,
    ) : StatsData

    // KMK -->
    data class Activity(
        val streak: StreakInfo,
        val heatmap: List<DayActivity>,
    ) : StatsData
    // KMK <--
}
