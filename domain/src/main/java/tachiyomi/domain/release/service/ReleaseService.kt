package tachiyomi.domain.release.service

import tachiyomi.domain.release.model.Release

interface ReleaseService {

    suspend fun latest(repository: String): Release

    // KMK -->
    suspend fun releaseNotes(repository: String): List<Release>
    // KMK <--
}
