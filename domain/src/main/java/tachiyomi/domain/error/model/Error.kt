package tachiyomi.domain.error.model

interface Error {
    val manga: ErrorManga
    val errorId: Long
}
