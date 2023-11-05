package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isInstalledFromFDroid
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import exh.syDebugVersion
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import uy.kohesive.injekt.injectLazy

class AppUpdateChecker {

    private val getApplicationRelease: GetApplicationRelease by injectLazy()

    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): GetApplicationRelease.Result {
        // Disable app update checks for older Android versions that we're going to drop support for
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        //     return GetApplicationRelease.Result.OsTooOld
        // }

        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    // SY -->
                    isPreviewBuildType,
                    // SY <--
                    context.isInstalledFromFDroid(),
                    BuildConfig.COMMIT_COUNT.toInt(),
                    BuildConfig.VERSION_NAME,
                    GITHUB_REPO,
                    // SY -->
                    syDebugVersion,
                    // SY <--
                    forceCheck,
                ),
            )

            when (result) {
                is GetApplicationRelease.Result.NewUpdate -> AppUpdateNotifier(context).promptUpdate(result.release)
                is GetApplicationRelease.Result.ThirdPartyInstallation -> AppUpdateNotifier(
                    context,
                ).promptFdroidUpdate()
                else -> {}
            }

            result
        }
    }
}

val GITHUB_REPO: String by lazy {
    // SY -->
    if (isPreviewBuildType) {
        "jobobby04/TachiyomiSYPreview"
    } else {
        "jobobby04/tachiyomiSY"
    }
    // SY <--
}
