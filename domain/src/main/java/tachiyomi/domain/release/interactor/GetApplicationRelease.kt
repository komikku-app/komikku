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
        val nextCheckTime = Instant.ofEpochMilli(lastChecked.get()).plus(3, ChronoUnit.DAYS)
        if (!arguments.forceCheck && now.isBefore(nextCheckTime)) {
            return Result.NoNewUpdate
        }

        // KMK -->
        val releases = service.releaseNotes(arguments.repository)
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
            isNewVersion && arguments.isThirdParty -> Result.ThirdPartyInstallation
            isNewVersion -> Result.NewUpdate(latest)
            else -> Result.NoNewUpdate
        }
    }

    // KMK -->
    suspend fun awaitReleaseNotes(arguments: Arguments): Result {
        val releases = service.releaseNotes(arguments.repository)
            .filter { !it.preRelease }
        val checksumRegex = """---(\R|.)*Checksums(\R|.)*""".toRegex()

        val release = releases.firstOrNull()
            ?.copy(
                info = releases.joinToString("\r---\r") {
                    "## ${it.version}\r\r" +
                        it.info.replace(checksumRegex, "")
                },
            )
        if (release == null) return Result.NoNewUpdate
        return Result.NewUpdate(release)
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
            // When has stable update but current app is preview version (same repo) expect new update
            // TODO: remove this when all users has finished switching from preview to stable
            if (newVersion.matches("""\d.\d.\d""".toRegex())) return true
            // KMK <--
            newVersion.toInt() > commitCount
        } else {
            // Release builds: based on releases in "komikku-app/komikku" repo
            // tagged as something like "v0.1.2"
            val oldVersion = versionName.replace("[^\\d.]".toRegex(), "")

            val newSemVer = newVersion.split(".").map { it.toInt() }
            val oldSemVer = oldVersion.split(".").map { it.toInt() }

            // KMK -->
            // When has stable update with preview version but current app is stable version expect no update
            // TODO: remove this when all users has finished switching from preview to stable
            if (newSemVer.size != oldSemVer.size) return false
            // KMK <--
            oldSemVer.mapIndexed { index, i ->
                if (newSemVer[index] > i) {
                    return true
                }
            }

            false
        }
    }

    data class Arguments(
        /** If current version is Preview (beta) build */
        val isPreview: Boolean,
        /** If current version is from third party */
        val isThirdParty: Boolean,
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
        data object ThirdPartyInstallation : Result
    }
}

// KMK --.
internal fun List<Release>.getLatest(): Release? {
    val checksumRegex = """---(\R|.)*Checksums(\R|.)*""".toRegex()

    return firstOrNull()
        ?.copy(
            info = joinToString("\r---\r") {
                "## ${it.version}\r\r" +
                    it.info.replace(checksumRegex, "")
            },
        )
}
// KMK <--
