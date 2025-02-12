package tachiyomi.data.error

import tachiyomi.domain.error.model.LibraryUpdateError

val libraryUpdateErrorMapper: (Long, Long, Long) -> LibraryUpdateError = { id, mangaId, messageId ->
    LibraryUpdateError(
        id = id,
        mangaId = mangaId,
        messageId = messageId,
    )
}
