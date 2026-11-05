package mihon.domain.migration.usecases

import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanDownloadMigrationTest {

    @Test
    fun `unique number moves`() {
        val oldChapter = chapter(id = 1L, number = 10.0)
        val target = chapter(id = 2L, number = 10.0)

        val plan = PlanDownloadMigration.plan(
            oldChapters = listOf(oldChapter),
            targetChapters = listOf(target),
            downloadedChapterIds = setOf(1L),
        )

        assertEquals(1L, plan.moves.single().oldChapter.id)
        assertEquals(2L, plan.moves.single().targetChapter.id)
        assertTrue(plan.skippedChapterIds.isEmpty())
    }

    @Test
    fun `missing number skips`() {
        val oldChapter = chapter(id = 1L, number = 4.0)

        val plan = PlanDownloadMigration.plan(
            oldChapters = listOf(oldChapter),
            targetChapters = listOf(chapter(id = 2L, number = 10.0)),
            downloadedChapterIds = setOf(1L),
        )

        assertTrue(plan.moves.isEmpty())
        assertEquals(listOf(1L), plan.skippedChapterIds)
    }

    @Test
    fun `duplicate target numbers prefer scanlator`() {
        val oldChapter = chapter(id = 1L, number = 10.0, scanlator = "FFS")
        val official = chapter(id = 2L, number = 10.0, sourceOrder = 1L, scanlator = "Official")
        val ffs = chapter(id = 3L, number = 10.0, sourceOrder = 9L, scanlator = "FFS")

        val plan = PlanDownloadMigration.plan(
            oldChapters = listOf(oldChapter),
            targetChapters = listOf(official, ffs),
            downloadedChapterIds = setOf(1L),
        )

        assertEquals(3L, plan.moves.single().targetChapter.id)
    }

    @Test
    fun `duplicate target numbers without scanlator use lowest source order`() {
        val oldChapter = chapter(id = 1L, number = 10.0, scanlator = null)
        val later = chapter(id = 2L, number = 10.0, sourceOrder = 6L, scanlator = "A")
        val earlier = chapter(id = 3L, number = 10.0, sourceOrder = 2L, scanlator = "B")

        val plan = PlanDownloadMigration.plan(
            oldChapters = listOf(oldChapter),
            targetChapters = listOf(later, earlier),
            downloadedChapterIds = setOf(1L),
        )

        assertEquals(3L, plan.moves.single().targetChapter.id)
    }

    @Test
    fun `chapter 10 does not match chapter 10_5`() {
        val oldChapter = chapter(id = 1L, number = 10.0)

        val plan = PlanDownloadMigration.plan(
            oldChapters = listOf(oldChapter),
            targetChapters = listOf(chapter(id = 2L, number = 10.5)),
            downloadedChapterIds = setOf(1L),
        )

        assertTrue(plan.moves.isEmpty())
        assertEquals(listOf(1L), plan.skippedChapterIds)
    }

    @Test
    fun `undownloaded and unrecognized chapters are not candidates`() {
        val downloaded = chapter(id = 1L, number = 10.0)
        val notDownloaded = chapter(id = 2L, number = 11.0)
        val unrecognized = chapter(id = 3L, number = -1.0)

        val plan = PlanDownloadMigration.plan(
            oldChapters = listOf(downloaded, notDownloaded, unrecognized),
            targetChapters = listOf(
                chapter(id = 10L, number = 10.0),
                chapter(id = 11L, number = 11.0),
            ),
            downloadedChapterIds = setOf(1L, 3L),
        )

        assertEquals(listOf(1L), plan.moves.map { it.oldChapter.id })
        assertTrue(plan.skippedChapterIds.isEmpty())
    }

    private fun chapter(
        id: Long,
        number: Double,
        sourceOrder: Long = id,
        scanlator: String? = null,
    ): Chapter = Chapter.create().copy(
        id = id,
        chapterNumber = number,
        sourceOrder = sourceOrder,
        name = "Chapter $number",
        scanlator = scanlator,
        url = "/chapter/$id/",
    )
}
