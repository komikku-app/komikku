package tachiyomi.domain.error.model

import java.io.Serializable

data class LibraryUpdateError(
    val id: Long,
    val mangaId: Long,
    val messageId: Long,
) : Serializable
