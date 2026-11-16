package app.komikku.desktop.ui

import app.komikku.desktop.ui.tabs.libraryPages
import app.komikku.desktop.ui.tabs.search
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

class LibraryPagesTest {

    private val default = category(Category.UNCATEGORIZED_ID, "", order = 0)
    private val reading = category(1, "Reading", order = 2)
    private val later = category(2, "Later", order = 1)
    private val hidden = category(3, "Hidden", order = 3, hidden = true)

    @Test
    fun `categories in user order with their manga sorted by title`() {
        val pages = libraryPages(
            categories = listOf(default, reading, later, hidden),
            libraryManga = listOf(
                libraryManga(1, "beta", listOf(1)),
                libraryManga(2, "Alpha", listOf(1, 2)),
                libraryManga(3, "Gamma", listOf(3)),
            ),
        )

        pages.map { it.category.name } shouldContainExactly listOf("Later", "Reading")
        pages.map { page -> page.manga.map { it.manga.id } } shouldContainExactly listOf(listOf(2L), listOf(2L, 1L))
    }

    @Test
    fun `default category only when it has manga or is the only one`() {
        libraryPages(listOf(default), emptyList()).map { it.category.id } shouldContainExactly listOf(0L)
        libraryPages(listOf(default, reading), emptyList()).map { it.category.id } shouldContainExactly listOf(1L)
        libraryPages(listOf(default, reading), listOf(libraryManga(1, "A", emptyList())))
            .map { it.category.id } shouldContainExactly listOf(0L, 1L)
    }

    @Test
    fun `search filters each page by title, author or genre`() {
        val pages = libraryPages(
            categories = listOf(reading, later),
            libraryManga = listOf(
                libraryManga(1, "Blue Period", listOf(1), author = "Yamaguchi"),
                libraryManga(2, "Dungeon Meshi", listOf(1, 2), genre = listOf("Comedy", "Fantasy")),
                libraryManga(3, "Frieren", listOf(2)),
            ),
        )
        pages.search("").map { page -> page.manga.map { it.manga.id } } shouldContainExactly listOf(listOf(2L, 3L), listOf(1L, 2L))
        pages.search("blue").map { page -> page.manga.map { it.manga.id } } shouldContainExactly listOf(emptyList(), listOf(1L))
        pages.search("YAMAGUCHI").map { page -> page.manga.map { it.manga.id } } shouldContainExactly listOf(emptyList(), listOf(1L))
        pages.search("comedy").map { page -> page.manga.map { it.manga.id } } shouldContainExactly listOf(listOf(2L), listOf(2L))
    }

    private fun category(id: Long, name: String, order: Long, hidden: Boolean = false) =
        Category(id = id, name = name, order = order, flags = 0, hidden = hidden)

    private fun libraryManga(
        id: Long,
        title: String,
        categories: List<Long>,
        author: String? = null,
        genre: List<String>? = null,
    ) = LibraryManga(
        // Not marked favorite: `Manga.title` would look up custom info through Injekt.
        manga = Manga.create().copy(id = id, ogTitle = title, ogAuthor = author, ogGenre = genre),
        categories = categories,
        totalChapters = 0,
        readCount = 0,
        bookmarkCount = 0,
        bookmarkReadCount = 0,
        chapterFlags = 0,
        latestUpload = 0,
        chapterFetchedAt = 0,
        lastRead = 0,
    )
}
