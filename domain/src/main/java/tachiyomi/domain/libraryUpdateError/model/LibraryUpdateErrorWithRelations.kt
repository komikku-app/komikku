package tachiyomi.domain.libraryUpdateError.model

data class LibraryUpdateErrorWithRelations(
    val mangaId: Long,
    val mangaTitle: String,
    val mangaSource: Long,
    val favorite: Boolean,
    val mangaThumbnail: String?,
    val coverLastModified: Long,
    val errorId: Long,
    val messageId: Long,
    val lastUpdate: Long,
)
