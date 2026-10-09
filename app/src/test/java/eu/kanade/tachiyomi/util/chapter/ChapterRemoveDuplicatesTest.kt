package eu.kanade.tachiyomi.util.chapter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ChapterRemoveDuplicatesTest {

    private fun chapter(id: Long, number: Double, scanlator: String? = null) =
        Chapter.create().copy(id = id, chapterNumber = number, scanlator = scanlator)

    @Test
    fun `keeps chapters with unrecognized numbers`() {
        val chapters = listOf(chapter(1, -1.0), chapter(2, -1.0), chapter(3, -1.0))

        assertEquals(listOf(1L, 2L, 3L), chapters.removeDuplicates(chapters[1]).map { it.id })
    }

    @Test
    fun `removes duplicates of recognized numbers preferring current scanlator`() {
        val chapters = listOf(
            chapter(1, 1.0, "A"),
            chapter(2, 1.0, "B"),
            chapter(3, -1.0),
            chapter(4, 2.0, "A"),
            chapter(5, 2.0, "B"),
        )

        assertEquals(listOf(2L, 3L, 5L), chapters.removeDuplicates(chapters[1]).map { it.id })
    }
}
