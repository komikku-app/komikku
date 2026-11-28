package tachiyomi.domain.release.interactor

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import java.time.Instant
import java.time.temporal.ChronoUnit

class GetApplicationRelease(
    private val service: ReleaseService,
    private val preferenceStore: PreferenceStore,
) {

    private val lastChecked: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_app_check"), 0)
    }

    suspend fun await(arguments: Arguments): Result {
        val now = Instant.now()

        // Limit checks to once every 3 days at most
        val nextCheckTime = Instant.ofEpochMilli(lastChecked.get()).plus(2, ChronoUnit.DAYS)
        if (!arguments.forceCheck && now.isBefore(nextCheckTime)) {
            return Result.NoNewUpdate
        }

        // KMK -->
        val releases = service.releaseNotes(arguments)
            .filter {
                !it.preRelease &&
                    isNewVersion(
                        arguments.isPreview,
                        arguments.commitCount,
                        arguments.versionName,
                        it.version,
                    )
            }

        val latest = releases.getLatest() ?: return Result.NoNewUpdate
        // KMK <--

        lastChecked.set(now.toEpochMilli())

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            isPreview = arguments.isPreview,
            commitCount = arguments.commitCount,
            versionName = arguments.versionName,
            versionTag = latest.version,
        )
        return when {
            isNewVersion -> Result.NewUpdate(latest)
            else -> Result.NoNewUpdate
        }
    }

    // KMK -->
    suspend fun awaitReleaseNotes(arguments: Arguments): Result {
        val releases = service.releaseNotes(arguments)
            .filter { !it.preRelease }

        val latest = releases.getLatest() ?: return Result.NoNewUpdate
        return Result.NewUpdate(latest)
    }
    // KMK <--

    /**
     * [isPreview] is if current version is Preview (beta) build
     *
     * [versionTag] is the version of new release
     *
     * Release (stable) version will compare with current's [versionName] ("v0.1.2")
     *
     * Preview (beta) version will compare with current's [commitCount] ("r1234")
     */
    private fun isNewVersion(
        isPreview: Boolean,
        commitCount: Int,
        versionName: String,
        versionTag: String,
    ): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")
        return if (isPreview) {
            // Preview builds: based on releases in "komikku-app/komikku-preview" repo
            // tagged as something like "r1234"
            // KMK -->
            // A tag that is not a commit count is not a newer build; throwing would fail the whole check
            newVersion.toIntOrNull()?.let { it > commitCount } ?: false
            // KMK <--
        } else {
            // Release builds: based on releases in "komikku-app/komikku" repo
            // tagged as something like "v0.1.2"
            val oldVersion = versionName.replace("[^\\d.]".toRegex(), "")

            // KMK -->
            // A tag that is not a version is not newer, and a missing part counts as 0 ("v1.0" is "v1.0.0"):
            // throwing here would fail the whole check, valid releases included
            val newSemVer = newVersion.split(".").map { it.toIntOrNull() ?: return false }
            val oldSemVer = oldVersion.split(".").map { it.toIntOrNull() ?: return false }
            // KMK <--

            oldSemVer.mapIndexed { index, i ->
                // KMK -->
                val newPart = newSemVer.getOrElse(index) { 0 }
                if (newPart > i) {
                    return true
                }
                if (newPart < i) return false
                // KMK <--
            }

            false
        }
    }

    data class Arguments(
        val isFoss: Boolean,
        /** If current version is Preview (beta) build */
        val isPreview: Boolean,
        /** Commit count of current version */
        val commitCount: Int,
        /** Current version name, could be version tag (v0.1.2) or commit count (r1234) */
        val versionName: String,
        /** Repository name */
        val repository: String,
        /** Force check for new update */
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }
}

// KMK --.
internal fun List<Release>.getLatest(): Release? {
    return firstOrNull()
        ?.copy(
            info = joinToString("\r-----\r") {
                "## ${it.version}\r\r" +
                    it.info
            },
        )
}
// KMK <--
