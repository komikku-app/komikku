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
        assertTrue(plan.orphanOldChapters.isEmpty())
    }

    @Test
    fun `missing number becomes an orphan candidate`() {
        val oldChapter = chapter(id = 1L, number = 4.0)

        val plan = PlanDownloadMigration.plan(
            oldChapters = listOf(oldChapter),
            targetChapters = listOf(chapter(id = 2L, number = 10.0)),
            downloadedChapterIds = setOf(1L),
        )

        assertTrue(plan.moves.isEmpty())
        assertEquals(listOf(1L), plan.orphanOldChapters.map { it.id })
    }

    @Test
    fun `partial overlap moves matches and orphans the rest`() {
        val oldChapters = (1L..8L).map { chapter(id = it, number = it.toDouble()) }
        val targetChapters = (4L..8L).map { chapter(id = 100L + it, number = it.toDouble()) }

        val plan = PlanDownloadMigration.plan(
            oldChapters = oldChapters,
            targetChapters = targetChapters,
            downloadedChapterIds = (1L..8L).toSet(),
        )

        assertEquals((4L..8L).toList(), plan.moves.map { it.oldChapter.id })
        assertEquals((1L..3L).toList(), plan.orphanOldChapters.map { it.id })
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
        assertTrue(plan.orphanOldChapters.isEmpty())
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
        assertEquals(listOf(1L), plan.orphanOldChapters.map { it.id })
    }

    @Test
    fun `undownloaded chapters are ignored and unrecognized downloads become orphans`() {
        val downloaded = chapter(id = 1L, number = 10.0)
        val notDownloaded = chapter(id = 2L, number = 11.0)
        val unrecognized = chapter(id = 3L, number = -1.0, name = "Bonus Story")

        val plan = PlanDownloadMigration.plan(
            oldChapters = listOf(downloaded, notDownloaded, unrecognized),
            targetChapters = listOf(
                chapter(id = 10L, number = 10.0),
                chapter(id = 11L, number = 11.0),
            ),
            downloadedChapterIds = setOf(1L, 3L),
        )

        assertEquals(listOf(1L), plan.moves.map { it.oldChapter.id })
        assertEquals(listOf(3L), plan.orphanOldChapters.map { it.id })
    }

    private fun chapter(
        id: Long,
        number: Double,
        sourceOrder: Long = id,
        scanlator: String? = null,
        name: String = "Chapter $number",
    ): Chapter = Chapter.create().copy(
        id = id,
        chapterNumber = number,
        sourceOrder = sourceOrder,
        name = name,
        scanlator = scanlator,
        url = "/chapter/$id/",
    )
}
