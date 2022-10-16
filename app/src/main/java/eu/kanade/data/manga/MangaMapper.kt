package eu.kanade.data.manga

import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.model.UpdateStrategy

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

val libraryManga: (Long, Long, String, String?, String?, String?, List<String>?, String, Long, String?, Boolean, Long?, Long?, Boolean, Long, Long, Long, Long, List<String>?, UpdateStrategy, Long, Long, Long, Long, Long, Long) -> LibraryManga =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, nextUpdate, initialized, viewerFlags, chapterFlags, coverLastModified, dateAdded, filteredScanlators, updateStrategy, unreadCount, readCount, latestUpload, chapterFetchedAt, lastRead, category ->
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
                filteredScanlators,
                updateStrategy,
            ),
            category = category,
            unreadCount = unreadCount,
            readCount = readCount,
            latestUpload = latestUpload,
            chapterFetchedAt = chapterFetchedAt,
            lastRead = lastRead,
        )
    }
