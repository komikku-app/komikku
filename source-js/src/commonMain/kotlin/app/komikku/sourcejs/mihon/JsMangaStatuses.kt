package app.komikku.sourcejs.mihon

import app.komikku.sourcejs.model.JsMangaStatus

/**
 * Mihon's `SManga` status codes (`SManga.UNKNOWN` … `SManga.ON_HIATUS`), repeated here because `commonMain` cannot see
 * `source-api`; the adapter tests check that they match.
 */
object MihonMangaStatus {
    const val UNKNOWN = 0
    const val ONGOING = 1
    const val COMPLETED = 2
    const val LICENSED = 3
    const val PUBLISHING_FINISHED = 4
    const val CANCELLED = 5
    const val ON_HIATUS = 6
}

/** The `SManga.status` for a Mangayomi status (contract 3.1: 0 ongoing … 4 publishing finished, 5 unknown). */
fun JsMangaStatus.toMihonStatus(): Int = when (this) {
    JsMangaStatus.ONGOING -> MihonMangaStatus.ONGOING
    JsMangaStatus.COMPLETED -> MihonMangaStatus.COMPLETED
    JsMangaStatus.ON_HIATUS -> MihonMangaStatus.ON_HIATUS
    JsMangaStatus.CANCELLED -> MihonMangaStatus.CANCELLED
    JsMangaStatus.PUBLISHING_FINISHED -> MihonMangaStatus.PUBLISHING_FINISHED
    JsMangaStatus.UNKNOWN -> MihonMangaStatus.UNKNOWN
}
