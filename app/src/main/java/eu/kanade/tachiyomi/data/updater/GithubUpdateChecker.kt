package eu.kanade.tachiyomi.data.updater

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.syDebugVersion
import uy.kohesive.injekt.injectLazy

class GithubUpdateChecker {

    private val networkService: NetworkHelper by injectLazy()

    private val repo: String by lazy {
        // Sy -->
        if (syDebugVersion != "0") {
            "jobobby04/TachiyomiSYPreview"
        } else {
            "jobobby04/tachiyomiSY"
        }
        // SY <--
    }

    suspend fun checkForUpdate(): GithubUpdateResult {
        return withIOContext {
            networkService.client
                .newCall(GET("https://api.github.com/repos/$repo/releases/latest"))
                .await()
                .parseAs<GithubRelease>()
                .let {
                    // Check if latest version is different from current version
                    if (/* SY --> */ isNewVersionSY(it.version) /* SY <-- */) {
                        GithubUpdateResult.NewUpdate(it)
                    } else {
                        GithubUpdateResult.NoNewUpdate
                    }
                }
        }
    }

    // SY -->
    private fun isNewVersionSY(versionTag: String) = (versionTag != BuildConfig.VERSION_NAME && (syDebugVersion == "0")) || ((syDebugVersion != "0") && versionTag != syDebugVersion)
    // SY <--

    private fun isNewVersion(versionTag: String): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")

        return if (BuildConfig.DEBUG) {
            // Preview builds: based on releases in "tachiyomiorg/tachiyomi-preview" repo
            // tagged as something like "r1234"
            newVersion.toInt() > BuildConfig.COMMIT_COUNT.toInt()
        } else {
            // Release builds: based on releases in "tachiyomiorg/tachiyomi" repo
            // tagged as something like "v0.1.2"
            newVersion != BuildConfig.VERSION_NAME
        }
    }
}
