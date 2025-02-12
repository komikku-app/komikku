package tachiyomi.data.error

import tachiyomi.domain.error.model.LibraryUpdateErrorMessage

val LibraryUpdateErrorMessageMapper: (Long, String) -> LibraryUpdateErrorMessage = { id, message ->
    LibraryUpdateErrorMessage(
        id = id,
        message = message,
    )
}
