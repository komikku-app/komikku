package tachiyomi.data.manga

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.view.LibraryView

val mangaMapper: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, List<String>?, UpdateStrategy) -> Manga =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, _, initialized, viewerFlags, chapterFlags, coverLastModified, dateAdded, filteredScanlators, updateStrategy ->
        Manga(
            id = id,
            source = source,
            favorite = favorite,
            lastUpdate = lastUpdate ?: 0,
            dateAdded = dateAdded,
            viewerFlags = viewerFlags,
            chapterFlags = chapterFlags,
            coverLastModified = coverLastModified,
            url = url,
            // SY -->
            ogTitle = title,
            ogArtist = artist,
            ogAuthor = author,
            ogDescription = description,
            ogGenre = genre,
            ogStatus = status,
            // SY <--
            thumbnailUrl = thumbnailUrl,
            updateStrategy = updateStrategy,
            initialized = initialized,
            // SY -->
            filteredScanlators = filteredScanlators,
            // SY <--
        )
    }

val libraryManga: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, List<String>?, UpdateStrategy, Long, Long, Long, Long, Long, Long, Long) -> LibraryManga =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, nextUpdate, initialized, viewerFlags, chapterFlags, coverLastModified, dateAdded, filteredScanlators, updateStrategy, totalCount, readCount, latestUpload, chapterFetchedAt, lastRead, bookmarkCount, category ->
        LibraryManga(
            manga = mangaMapper(
                id,
                source,
                url,
                artist,
                author,
                description,
                genre,
                title,
                status,
                thumbnailUrl,
                favorite,
                lastUpdate,
                nextUpdate,
                initialized,
                viewerFlags,
                chapterFlags,
                coverLastModified,
                dateAdded,
                // SY -->
                filteredScanlators,
                // SY <--
                updateStrategy,
            ),
            category = category,
            totalChapters = totalCount,
            readCount = readCount,
            bookmarkCount = bookmarkCount,
            latestUpload = latestUpload,
            chapterFetchedAt = chapterFetchedAt,
            lastRead = lastRead,
        )
    }

val libraryViewMapper: (LibraryView) -> LibraryManga = {
    LibraryManga(
        Manga(
            id = it._id,
            source = it.source,
            favorite = it.favorite,
            lastUpdate = it.last_update ?: 0,
            dateAdded = it.date_added,
            viewerFlags = it.viewer,
            chapterFlags = it.chapter_flags,
            coverLastModified = it.cover_last_modified,
            url = it.url,
            ogTitle = it.title,
            ogArtist = it.artist,
            ogAuthor = it.author,
            ogDescription = it.description,
            ogGenre = it.genre,
            ogStatus = it.status,
            thumbnailUrl = it.thumbnail_url,
            updateStrategy = it.update_strategy,
            initialized = it.initialized,
            filteredScanlators = it.filtered_scanlators,
        ),
        it.category,
        it.totalCount,
        it.readCount,
        it.bookmarkCount,
        it.latestUpload,
        it.chapterFetchedAt,
        it.lastRead,
    )
}
