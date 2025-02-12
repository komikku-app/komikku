package tachiyomi.domain.error.model

import tachiyomi.domain.manga.model.MangaCover

data class ErrorManga(
    val mangaId: Long,
    val mangaTitle: String,
    val mangaSource: Long,
    val mangaCover: MangaCover,
)
