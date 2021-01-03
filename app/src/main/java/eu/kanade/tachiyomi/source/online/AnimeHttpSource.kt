package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable
import okhttp3.Request
import okhttp3.Response


/**
 * A source that will return anime instead of manga.
 * Chapters are episodes, pages are videos, page numbers are video quality
 * The rest works the same as a standard HttpSource
 */
abstract class AnimeHttpSource : HttpSource() {

    abstract fun fetchPopularAnime(page:Int): Observable<MangasPage>

    abstract fun fetchLatestAnime(page: Int): Observable<MangasPage>

    abstract fun fetchAnimeDetails(anime: SManga): Observable<SManga>

    abstract fun fetchEpisodeList(anime: SManga): Observable<List<SChapter>>

    abstract fun fetchVideoList(chapter: SChapter): Observable<List<Page>>

    abstract fun fetchSearchAnime(page: Int, query: String, filters: FilterList): Observable<MangasPage>

    //renames
    override fun fetchPopularManga(page:Int): Observable<MangasPage> = fetchPopularAnime(page)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = fetchAnimeDetails(manga)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = fetchEpisodeList(manga)

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = fetchVideoList(chapter)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchSearchAnime(page, query, filters)

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = fetchLatestAnime(page)

    //unused
    override fun imageUrlParse(response: Response) = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun mangaDetailsParse(response: Response): SManga = throw Exception("Not used")

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")

    override fun popularMangaParse(response: Response): MangasPage = throw Exception("Not used")

    override fun chapterListParse(response: Response): List<SChapter> = throw Exception("Not Used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

}
