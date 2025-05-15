package tachiyomi.data.libraryUpdateError

import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateError

val libraryUpdateErrorMapper: (Long, Long, Long, Long) -> LibraryUpdateError = { id, mangaId, messageId, lastUpdate ->
    LibraryUpdateError(
        id = id,
        mangaId = mangaId,
        messageId = messageId,
        lastUpdate = lastUpdate,
    )
}
