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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * KMK: the desktop counterpart of Android's `AppUpdateChecker` (P6), on the same interactor and rules:
 * [GetApplicationRelease] reads the GitHub releases of [DesktopUpdaterConfig.repository] (the desktop `ReleaseService`
 * keeps only releases with an installer for this OS and a tag that can be compared), skips pre-releases and compares the
 * tags with the desktop app's own version (stable: version name, preview: commit count). A forced check (About →
 * "Check for updates") always contacts GitHub.
 *
 * Throttling of automatic checks (start-up and scheduled): the interactor's own two-day throttle (`last_app_check`, the
 * Android app-state key) only starts after it *found* an update, so on its own an up-to-date desktop would ask GitHub at
 * every start. The desktop therefore keeps its own "last check that got an answer" time
 * ([DesktopSchedulerPreferences.lastAppUpdateCheck], also the scheduled task's last run): it is written only after a
 * check that got an answer (update or not), never after a failure, and an automatic check within
 * [AUTOMATIC_CHECK_INTERVAL] of it does not contact GitHub ([Check.Throttled]). After a failure (e.g. offline) an
 * automatic check within [FAILURE_RETRY_DELAY] reports that failure again without contacting GitHub, so the start-up
 * check and the scheduler's catch-up right after it do not both try; the scheduled task retries after its short delay.
 * Checks never run at the same time.
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

        /** An automatic check soon after one that got an answer: nothing was contacted. */
        data object Throttled : Check

        /** The build flag is off or the version cannot be compared: nothing was contacted. */
        data object Disabled : Check
        data class Failed(val error: Throwable) : Check
    }

    private val mutex = Mutex()

    /** The last failed check of this process (time, failure), for [FAILURE_RETRY_DELAY]; guarded by [mutex]. */
    private var lastFailure: Pair<Long, Check.Failed>? = null

    private val _available = MutableStateFlow<Release?>(null)

    /** The newest release found by the last check that found one, until the user dismisses it ([dismiss]). */
    val available: StateFlow<Release?> = _available.asStateFlow()

    val isEnabled: Boolean get() = config.isActive

    suspend fun check(forceCheck: Boolean = false): Check {
        if (!isEnabled) return Check.Disabled
        return mutex.withLock {
            if (!forceCheck) {
                val at = now()
                val lastAnswer = schedulerPreferences.lastAppUpdateCheck().get()
                // A stored time in the future (the clock moved back) does not block checks
                if (lastAnswer in (at - AUTOMATIC_CHECK_INTERVAL.inWholeMilliseconds + 1)..at) return@withLock Check.Throttled
                lastFailure?.let { (failedAt, failed) ->
                    if (failedAt in (at - FAILURE_RETRY_DELAY.inWholeMilliseconds + 1)..at) return@withLock failed
                }
            }
            contactGitHub(forceCheck).also { result ->
                if (result is Check.Failed) {
                    lastFailure = now() to result
                } else {
                    lastFailure = null
                    schedulerPreferences.lastAppUpdateCheck().set(now())
                }
            }
        }
    }

    private suspend fun contactGitHub(forceCheck: Boolean): Check {
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
     * Android's start-up check (`MainActivity.CheckForUpdates`): an automatic check, so it does not contact GitHub
     * within [AUTOMATIC_CHECK_INTERVAL] of the last check that got an answer. Must be called before the scheduler starts:
     * the scheduler's catch-up then waits for it (checks never run at the same time) and finds the task's last run
     * already updated, or the fresh failure.
     */
    fun checkOnStart(scope: CoroutineScope): Job? {
        if (!isEnabled) return null
        return scope.launch { check() }
    }

    companion object {
        /** Android's throttle of automatic checks (`GetApplicationRelease`: two days). */
        val AUTOMATIC_CHECK_INTERVAL = 2.days

        /** How long an automatic check reuses a failure of this process; the scheduler's `Busy` retry delay. */
        val FAILURE_RETRY_DELAY = 10.minutes
    }
}

const val APP_UPDATE_CHECK_TASK = "app update check"

/**
 * Android's `AppUpdateJob` (every 3 days, a non-forced check): off when the updater is disabled. The last run is the
 * last check that got an answer ([DesktopSchedulerPreferences.lastAppUpdateCheck], written by the checker). A failed
 * check (e.g. offline) is not recorded and retried after the scheduler's short delay ([DesktopScheduler.Outcome.Busy],
 * 10 minutes) instead of waiting a whole interval.
 */
fun appUpdateCheckTask(
    checker: DesktopAppUpdateChecker,
    schedulerPreferences: DesktopSchedulerPreferences,
) = DesktopScheduler.Task(
    name = APP_UPDATE_CHECK_TASK,
    intervalMillis = { if (checker.isEnabled) 3.days.inWholeMilliseconds else 0L },
    lastRun = { schedulerPreferences.lastAppUpdateCheck().get() },
    retryDelay = DesktopAppUpdateChecker.FAILURE_RETRY_DELAY,
    run = {
        when (checker.check()) {
            is DesktopAppUpdateChecker.Check.Failed -> DesktopScheduler.Outcome.Busy
            else -> DesktopScheduler.Outcome.Done
        }
    },
)
