package tachiyomi.data.release

import dev.icerock.moko.graphics.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class ReleaseServiceImpl(
    private val networkService: NetworkHelper,
    private val json: Json,
) : ReleaseService {

    override suspend fun latest(repository: String): Release {
        return with(json) {
            networkService.client
                .newCall(GET("https://api.github.com/repos/$repository/releases/latest"))
                .awaitSuccess()
                .parseAs<GithubRelease>()
                .let(releaseMapper)
        }
    }

    // KMK -->
    override suspend fun releaseNotes(repository: String): List<Release> {
        val releases = if (BuildConfig.DEBUG) {
            "https://raw.githubusercontent.com/$repository/refs/heads/auto-install-app-update/app/src/debug/res/raw/releases.json"
        } else {
            "https://api.github.com/repos/$repository/releases"
        }
        return with(json) {
            networkService.client
                .newCall(GET(releases))
                .awaitSuccess()
                .parseAs<List<GithubRelease>>()
                .map(releaseMapper)
        }
    }
    // KMK <--
}
