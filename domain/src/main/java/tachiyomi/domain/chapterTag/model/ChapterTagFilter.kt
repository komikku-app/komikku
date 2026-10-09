package tachiyomi.domain.chapterTag.model

/**
 * Per-manga chapter tag filter selection.
 *
 * A chapter is kept when it carries every [included] tag (vacuously true when nothing is
 * included) and none of the [excluded] tags.
 */
data class ChapterTagFilter(
    val included: Set<Long> = emptySet(),
    val excluded: Set<Long> = emptySet(),
) {
    val isActive: Boolean
        get() = included.isNotEmpty() || excluded.isNotEmpty()

    companion object {
        val Empty = ChapterTagFilter()
    }
}
