package tachiyomi.domain.error.model

data class LibraryUpdateErrorWithRelations(
    val manga: ErrorManga,
    val errorId: Long,
    val messageId: Long,
)
