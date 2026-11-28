package eu.kanade.tachiyomi.ui.browse

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class BulkFavoriteScreenModelTest {

    private val model = BulkFavoriteScreenModel(
        sourceManager = mockk(),
        libraryPreferences = mockk(),
        getDuplicateLibraryManga = mockk(),
        getCategories = mockk(),
        setMangaCategories = mockk(),
        updateManga = mockk(),
        coverCache = mockk(),
        setMangaDefaultChapterFlags = mockk(),
        addTracks = mockk(),
        updateMangaFromRemote = mockk(),
    )

    private val first = Manga.create().copy(id = 1L, source = 1L, url = "/a", ogTitle = "A")
    private val second = Manga.create().copy(id = 2L, source = 1L, url = "/b", ogTitle = "B")

    @Test
    fun `reverseSelection unselects the selected entries and selects the others`() {
        model.toggleSelection(first)

        model.reverseSelection(listOf(first, second))

        model.state.value.selection.map { it.id } shouldBe listOf(second.id)
    }

    @Test
    fun `reverseSelection compares the entries by id`() {
        model.toggleSelection(first)
        // The same entry after its row changed (e.g. its details were fetched)
        val changed = first.copy(ogTitle = "A (updated)", lastUpdate = 1L)

        model.reverseSelection(listOf(changed, second))

        model.state.value.selection.map { it.id } shouldBe listOf(second.id)
    }
}
