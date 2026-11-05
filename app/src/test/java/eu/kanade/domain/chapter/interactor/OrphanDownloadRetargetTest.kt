package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.interactor.OrphanDownloadRetarget.Decision
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OrphanDownloadRetargetTest {

    private val mangaTitle = "Gentleman's Maid Sophie"

    @Test
    fun `unique catalog number is the repair target`() {
        val catalog = chapter(id = 5L, number = 10.0, sourceOrder = 3L)
        val decision = OrphanDownloadRetarget.choose(
            folderName = "Fallguy69420_Chapter 10",
            mangaTitle = mangaTitle,
            chapters = listOf(catalog, orphan(id = 9L, number = 69420.0)),
        )

        val retarget = assertIs<Decision.Retarget>(decision)
        assertEquals(5L, retarget.chapter.id)
    }

    @Test
    fun `ambiguous number with a scanlator prefix picks that scanlator`() {
        val official = chapter(id = 1L, number = 10.0, sourceOrder = 1L, scanlator = "official")
        val unofficial = chapter(id = 2L, number = 10.0, sourceOrder = 4L, scanlator = "unofficial")

        val decision = OrphanDownloadRetarget.choose(
            folderName = "unofficial_Ch. 10",
            mangaTitle = mangaTitle,
            chapters = listOf(official, unofficial),
        )

        val retarget = assertIs<Decision.Retarget>(decision)
        assertEquals(2L, retarget.chapter.id)
    }

    @Test
    fun `ambiguous Fallguy folder picks the lowest source order`() {
        val later = chapter(id = 8L, number = 10.0, sourceOrder = 8L, scanlator = "Group A")
        val earlier = chapter(id = 2L, number = 10.0, sourceOrder = 2L, scanlator = "Group B")

        val decision = OrphanDownloadRetarget.choose(
            folderName = "Fallguy69420_Chapter 10",
            mangaTitle = mangaTitle,
            chapters = listOf(later, earlier),
        )

        val retarget = assertIs<Decision.Retarget>(decision)
        assertEquals(2L, retarget.chapter.id)
    }

    @Test
    fun `unparsed folder name is left alone`() {
        val decision = OrphanDownloadRetarget.choose(
            folderName = "Bonus Story",
            mangaTitle = mangaTitle,
            chapters = listOf(chapter(id = 1L, number = 10.0, sourceOrder = 1L)),
        )

        assertIs<Decision.Leave>(decision)
    }

    private fun chapter(
        id: Long,
        number: Double,
        sourceOrder: Long,
        scanlator: String? = null,
    ): Chapter = Chapter.create().copy(
        id = id,
        chapterNumber = number,
        sourceOrder = sourceOrder,
        name = "Chapter $number",
        scanlator = scanlator,
        url = "/chapter/$id/",
    )

    private fun orphan(id: Long, number: Double): Chapter = Chapter.create().copy(
        id = id,
        chapterNumber = number,
        name = "Fallguy69420_Chapter 10",
        url = "orphaned://Fallguy69420_Chapter 10",
        sourceOrder = -1L,
    )
}
