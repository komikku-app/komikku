package app.komikku.desktop.updater

import app.komikku.desktop.scheduler.DesktopScheduler
import app.komikku.desktop.scheduler.DesktopSchedulerPreferences
import exh.log.xLogE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import kotlin.time.Duration.Companion.days

/**
 * KMK: the desktop counterpart of Android's `AppUpdateChecker` (P6), on the same interactor and rules:
 * [GetApplicationRelease] reads the GitHub releases of [DesktopUpdaterConfig.repository] (the desktop `ReleaseService`
 * keeps only releases with an installer for this OS), skips pre-releases, compares the tags with the desktop app's own
 * version (stable: version name, preview: commit count) and throttles automatic checks to one every two days
 * (`last_app_check`, the same app-state key as Android). A forced check (About → "Check for updates") ignores the
 * throttle.
 *
 * A found update is published in [available]; the window shows it (release notes, "Open on GitHub" opens the release
 * page in the browser). There is no in-app download / install on desktop: installers are per OS and per package format
 * and need the OS installer anyway (and are unsigned so far), so the release page is the update path.
 */
class DesktopAppUpdateChecker(
    private val getApplicationRelease: GetApplicationRelease,
    val config: DesktopUpdaterConfig,
    private val schedulerPreferences: DesktopSchedulerPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {

    sealed interface Check {
        data class NewUpdate(val release: Release) : Check
        data object NoNewUpdate : Check

        /** The build flag is off or the version cannot be compared: nothing was contacted. */
        data object Disabled : Check
        data class Failed(val error: Throwable) : Check
    }

    private val _available = MutableStateFlow<Release?>(null)

    /** The newest release found by the last check that found one, until the user dismisses it ([dismiss]). */
    val available: StateFlow<Release?> = _available.asStateFlow()

    val isEnabled: Boolean get() = config.isActive

    suspend fun check(forceCheck: Boolean = false): Check {
        if (!isEnabled) return Check.Disabled
        return try {
            val result = withIOContext {
                getApplicationRelease.await(
                    GetApplicationRelease.Arguments(
                        isFoss = false,
                        isPreview = config.isPreview,
                        commitCount = config.commitCount,
                        versionName = config.versionName,
                        repository = config.repository,
                        forceCheck = forceCheck,
                    ),
                )
            }
            when (result) {
                is GetApplicationRelease.Result.NewUpdate -> {
                    _available.value = result.release
                    Check.NewUpdate(result.release)
                }
                // `OsTooOld` is Android-only (never returned by the interactor today)
                else -> Check.NoNewUpdate
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            xLogE("Unable to check for an app update", e)
            Check.Failed(e)
        }
    }

    fun dismiss() {
        _available.value = null
    }

    /**
     * Android's start-up check (`MainActivity.CheckForUpdates`, throttled by the interactor). It counts as the
     * scheduler's last run, so the scheduler's catch-up right after start does not check a second time.
     */
    fun checkOnStart(scope: CoroutineScope): Job? {
        if (!isEnabled) return null
        schedulerPreferences.lastAppUpdateCheck().set(now())
        return scope.launch { check() }
    }
}

const val APP_UPDATE_CHECK_TASK = "app update check"

/**
 * Android's `AppUpdateJob` (every 3 days, a non-forced check): off when the updater is disabled. A failed check (e.g.
 * offline) waits a whole interval like the job's `Result.failure()`; the next app start checks again anyway.
 */
fun appUpdateCheckTask(
    checker: DesktopAppUpdateChecker,
    schedulerPreferences: DesktopSchedulerPreferences,
    now: () -> Long = System::currentTimeMillis,
) = DesktopScheduler.Task(
    name = APP_UPDATE_CHECK_TASK,
    intervalMillis = { if (checker.isEnabled) 3.days.inWholeMilliseconds else 0L },
    lastRun = { schedulerPreferences.lastAppUpdateCheck().get() },
    run = {
        schedulerPreferences.lastAppUpdateCheck().set(now())
        when (checker.check()) {
            is DesktopAppUpdateChecker.Check.Failed -> DesktopScheduler.Outcome.Failed
            else -> DesktopScheduler.Outcome.Done
        }
    },
)
