package tachiyomi.domain.release.service

import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release

interface ReleaseService {

    suspend fun latest(arguments: GetApplicationRelease.Arguments): Release?

    // KMK -->
    suspend fun releaseNotes(arguments: GetApplicationRelease.Arguments): List<Release>
    // KMK <--
}
