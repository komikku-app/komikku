package tachiyomi.data.error

import tachiyomi.domain.error.model.DatabaseError
import tachiyomi.domain.error.model.DatabaseErrorType
import tachiyomi.domain.error.model.ErrorManga
import tachiyomi.domain.manga.model.MangaCover

fun databaseErrorMapper(
    mangaId: Long,
    mangaTitle: String,
    mangaSource: Long,
    favorite: Boolean,
    mangaThumbnail: String?,
    coverLastModified: Long,
    errorType: DatabaseErrorType,
): DatabaseError =
    DatabaseError(
        errorId = mangaId,
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
        errorType = errorType,
    )
