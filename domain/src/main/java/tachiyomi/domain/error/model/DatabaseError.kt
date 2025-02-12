package tachiyomi.domain.error.model

data class DatabaseError(
    val errorId: Long,
    val manga: ErrorManga,
    val errorType: DatabaseErrorType,
)

data class DatabaseErrorCount(
    val url: String,
    val sourceId: Long,
    val count: Int,
)

enum class DatabaseErrorType(val message: String) {
    DUPLICATE_MANGA_URL("Duplicate entry"),
    DUPLICATE_CHAPTER("Duplicate chapter"),
}
