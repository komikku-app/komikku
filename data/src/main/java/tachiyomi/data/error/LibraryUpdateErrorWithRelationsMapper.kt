package tachiyomi.data.error

import tachiyomi.domain.error.model.ErrorManga
import tachiyomi.domain.error.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.manga.model.MangaCover

val libraryUpdateErrorWithRelationsMapper:
    (Long, String, Long, Boolean, String?, Long, Long, Long) -> LibraryUpdateErrorWithRelations =
    { mangaId, mangaTitle, mangaSource, favorite, mangaThumbnail, coverLastModified, errorId, messageId ->
        LibraryUpdateErrorWithRelations(
            ErrorManga(
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                mangaSource = mangaSource,
                mangaCover = MangaCover(
                    mangaId = mangaId,
                    sourceId = mangaSource,
                    isMangaFavorite = favorite,
                    ogUrl = mangaThumbnail,
                    lastModified = coverLastModified,
                ),
            ),
            errorId = errorId,
            messageId = messageId,
        )
    }
