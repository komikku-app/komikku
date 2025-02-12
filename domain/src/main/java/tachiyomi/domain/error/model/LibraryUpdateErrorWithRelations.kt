package tachiyomi.domain.error.model

data class LibraryUpdateErrorWithRelations(
    override val manga: ErrorManga,
    override val errorId: Long,
    val messageId: Long,
) : Error
