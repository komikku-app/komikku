package eu.kanade.tachiyomi.data.updater.github

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.updater.UpdateResult
import exh.syDebugVersion

class GithubUpdateChecker {

    private val service: GithubService = GithubService.create()

    private val repo: String by lazy {
        // Sy -->
        if (syDebugVersion != "0") {
            "jobobby04/TachiyomiSYPreview"
        } else {
            "jobobby04/tachiyomiSY"
        }
        // SY <--
    }

    suspend fun checkForUpdate(): UpdateResult {
        val release = service.getLatestVersion(repo)

        // Check if latest version is different from current version
        // SY -->
        val newVersion = release.version
        return if ((newVersion != BuildConfig.VERSION_NAME && (syDebugVersion == "0")) || ((syDebugVersion != "0") && newVersion != syDebugVersion)) {
            // SY <--
            GithubUpdateResult.NewUpdate(release)
        } else {
            GithubUpdateResult.NoNewUpdate()
        }
    }

    private fun isNewVersion(versionTag: String): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")

        return if (BuildConfig.DEBUG) {
            // Preview builds: based on releases in "tachiyomiorg/android-app-preview" repo
            // tagged as something like "r1234"
            newVersion.toInt() > BuildConfig.COMMIT_COUNT.toInt()
        } else {
            // Release builds: based on releases in "inorichi/tachiyomi" repo
            // tagged as something like "v0.1.2"
            newVersion != BuildConfig.VERSION_NAME
        }
    }
}
