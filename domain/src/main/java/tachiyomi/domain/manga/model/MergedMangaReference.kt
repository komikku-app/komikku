package tachiyomi.domain.manga.model

data class MergedMangaReference(
    // Tag identifier, unique
    val id: Long,

    // The manga where it grabs the updated manga info
    val isInfoManga: Boolean,

    // If false the manga will not grab chapter updates
    val getChapterUpdates: Boolean,

    // The mode in which the episodes are handled, only set in the main merge reference
    val chapterSortMode: Int,

    // chapter priority the deduplication uses
    val chapterPriority: Int,

    // Set if you want it to download new episodes
    val downloadChapters: Boolean,

    // merged manga this reference is attached to
    val mergeId: Long?,

    // merged manga url this reference is attached to
    val mergeUrl: String,

    // manga id included in the merge this reference is attached to
    val mangaId: Long?,

    // manga url included in the merge this reference is attached to
    val mangaUrl: String,

    // source of the manga that is merged into this merge
    val mangaSourceId: Long,
) {
    companion object {
        const val EPISODE_SORT_NONE = 0
        const val EPISODE_SORT_NO_DEDUPE = 1
        const val EPISODE_SORT_PRIORITY = 2
        const val EPISODE_SORT_MOST_EPISODES = 3
        const val EPISODE_SORT_HIGHEST_EPISODE_NUMBER = 4
    }
}
