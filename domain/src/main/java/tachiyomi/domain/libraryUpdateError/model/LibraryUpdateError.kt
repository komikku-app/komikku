package tachiyomi.domain.libraryUpdateError.model

import java.io.Serializable

data class LibraryUpdateError(
    val id: Long,
    val mangaId: Long,
    val messageId: Long,
    val lastUpdate: Long = 0L,
) : Serializable
