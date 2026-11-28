package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.repository.SourceRepository

class GetSourcesWithFavoriteCountTest {

    private fun source(id: Long, name: String, isStub: Boolean = false) =
        Source(id = id, lang = "en", name = name, supportsLatest = true, isStub = isStub)

    private val sources = listOf(
        source(1L, "Alpha") to 5L,
        source(2L, "Beta") to 1L,
        source(3L, "Missing", isStub = true) to 3L,
    )

    private fun sortedNames(direction: SetMigrateSorting.Direction, mode: SetMigrateSorting.Mode): List<String> {
        val repository = mockk<SourceRepository> {
            every { getSourcesWithFavoriteCount() } returns flowOf(sources)
        }
        val preferences = mockk<SourcePreferences> {
            every { migrationSortingDirection().changes() } returns flowOf(direction)
            every { migrationSortingMode().changes() } returns flowOf(mode)
        }
        return runBlocking {
            GetSourcesWithFavoriteCount(repository, preferences).subscribe().first().map { it.first.name }
        }
    }

    @Test
    fun `not installed sources come first when sorted ascending`() {
        sortedNames(SetMigrateSorting.Direction.ASCENDING, SetMigrateSorting.Mode.ALPHABETICAL) shouldBe
            listOf("Missing", "Alpha", "Beta")
        sortedNames(SetMigrateSorting.Direction.ASCENDING, SetMigrateSorting.Mode.TOTAL) shouldBe
            listOf("Missing", "Beta", "Alpha")
    }

    @Test
    fun `not installed sources come first when sorted descending`() {
        sortedNames(SetMigrateSorting.Direction.DESCENDING, SetMigrateSorting.Mode.ALPHABETICAL) shouldBe
            listOf("Missing", "Beta", "Alpha")
        sortedNames(SetMigrateSorting.Direction.DESCENDING, SetMigrateSorting.Mode.TOTAL) shouldBe
            listOf("Missing", "Alpha", "Beta")
    }
}
