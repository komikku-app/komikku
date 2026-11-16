package eu.kanade.tachiyomi.source.online.all

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/*
 * KMK: desktop placeholders for the SY built-in sources that `desktopCopy` borrowed files test for
 * with `is` (`UpdateMangaFromRemote`, the reader's `ChapterLoader`). The built-in E-Hentai and merged
 * sources are not available on desktop (PROGRESS.md, 5.2), so these types are never instantiated and
 * the `is` checks are always false; only the members the borrowed files call are declared, with the
 * Android signatures.
 */

/** Placeholder for the SY E-Hentai source (`app/…/source/online/all/EHentai.kt`). */
abstract class EHentai : HttpSource() {
    abstract suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
        throttleFunc: suspend () -> Unit,
    ): SMangaUpdate
}

/** Placeholder for the SY merged source (`app/…/source/online/all/MergedSource.kt`). */
abstract class MergedSource : HttpSource() {
    abstract suspend fun fetchChaptersAndSync(manga: Manga, downloadChapters: Boolean = true): List<Chapter>
}
