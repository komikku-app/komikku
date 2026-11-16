package app.komikku.desktop.extension.fixture

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate

/**
 * Extension-like classes for the class loader tests. They are packed into a jar from the test
 * classpath, so the extension class loader defines its own copies (child first) while `Source`,
 * `SManga` and the coroutine machinery come from the host.
 */
class KotlinFixtureSource(
    override val id: Long = 1001L,
    override val name: String = "Kotlin fixture",
    override val lang: String = "en",
) : Source {

    override val supportsLatest: Boolean = true

    override suspend fun getPopularManga(page: Int): MangasPage =
        MangasPage(
            listOf(
                SManga.create().apply {
                    title = "Popular $page"
                    url = "/p/$page"
                },
            ),
            hasNextPage = false,
        )

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        MangasPage(
            listOf(
                SManga.create().apply {
                    title = query
                    url = "/s"
                },
            ),
            hasNextPage = false,
        )

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(manga, chapters)

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}

class KotlinFixtureFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        KotlinFixtureSource(id = 2001L, name = "Factory A"),
        KotlinFixtureSource(id = 2002L, name = "Factory B", lang = "fr"),
    )
}

class NotASource
