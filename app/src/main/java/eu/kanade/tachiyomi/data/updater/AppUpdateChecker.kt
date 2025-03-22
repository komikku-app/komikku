package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.AppUpdatePolicy
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class AppUpdateChecker(
    // KMK -->
    private val peekIntoPreview: Boolean = false,
    // KMK <--
) {

    private val getApplicationRelease: GetApplicationRelease by injectLazy()

    // KMK -->
    private val preferences by lazy { Injekt.get<UnsortedPreferences>() }
    // KMK <--

    suspend fun checkForUpdate(
        context: Context,
        forceCheck: Boolean = false,
        // KMK -->
        autoUpdate: Boolean = AppUpdatePolicy.DISABLE_AUTO_DOWNLOAD !in preferences.appShouldAutoUpdate().get(),
        // KMK <--
    ): GetApplicationRelease.Result {
        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    isPreview = BuildConfig.PREVIEW || peekIntoPreview,
                    commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                    versionName = BuildConfig.VERSION_NAME,
                    repository = getGithubRepo(peekIntoPreview),
                    forceCheck = forceCheck,
                ),
            )

            // KMK -->
            if (!peekIntoPreview) {
                // KMK <--
                when (result) {
                    is GetApplicationRelease.Result.NewUpdate -> {
                        // KMK -->
                        AppUpdateNotifier.releasePageUrl = result.release.releaseLink
                        // KMK <--
                        AppUpdateNotifier(context).promptUpdate(result.release)
                    }

                    else -> {}
                }

                // KMK -->
                if (autoUpdate && result is GetApplicationRelease.Result.NewUpdate) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AppUpdateDownloadJob.start(
                            context = context,
                            url = result.release.getDownloadLink(),
                            title = result.release.version,
                            scheduled = true,
                        )
                    }
                }
                // KMK <--
            }

            result
        }
    }

    // KMK -->
    suspend fun getReleaseNotes(context: Context): GetApplicationRelease.Result {
        return withIOContext {
            getApplicationRelease.awaitReleaseNotes(
                GetApplicationRelease.Arguments(
                    isPreview = BuildConfig.PREVIEW || peekIntoPreview,
                    commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                    versionName = BuildConfig.VERSION_NAME,
                    repository = getGithubRepo(peekIntoPreview),
                ),
            )
        }
    }
    // KMK <--
}

val GITHUB_REPO: String by lazy { getGithubRepo() }

fun getGithubRepo(peekIntoPreview: Boolean = false): String =
    if (BuildConfig.PREVIEW || peekIntoPreview) {
        "komikku-app/komikku-preview"
    } else {
        "komikku-app/komikku"
    }

val RELEASE_TAG: String by lazy { getReleaseTag() }

fun getReleaseTag(peekIntoPreview: Boolean = false): String =
    if (BuildConfig.PREVIEW || peekIntoPreview) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
