package tachiyomi.domain.error.model

data class DatabaseError(
    override val manga: ErrorManga,
    override val errorId: Long,
    val errorType: DatabaseErrorType,
) : Error

data class DatabaseErrorCount(
    val url: String,
    val sourceId: Long,
    val count: Int,
)

enum class DatabaseErrorType(val message: String) {
    DUPLICATE_MANGA_URL("Duplicate entry"),
    DUPLICATE_CHAPTER("Duplicate chapter"),
}
