package app.komikku.desktop.updater

import app.komikku.desktop.scheduler.DesktopScheduler
import app.komikku.desktop.scheduler.DesktopSchedulerPreferences
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.DesktopPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.data.release.GitHubAsset
import tachiyomi.data.release.GithubRelease
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.days

/**
 * P6: the desktop update check on Android's [GetApplicationRelease] with a fake release source shaped like the GitHub
 * releases API (newest first), filtered through the desktop installer selection like the app's `ReleaseServiceImpl`.
 */
class DesktopAppUpdateCheckerTest {

    @TempDir
    lateinit var dir: File

    /** GitHub's `/releases` list for a repository; [failure] simulates the network failing. */
    private class FakeReleases(private val config: () -> DesktopUpdaterConfig) : ReleaseService {
        val releases = mutableMapOf<String, List<GithubRelease>>()
        var failure: Exception? = null
        val requested = mutableListOf<String>()

        override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? = error("not used by the app")

        override suspend fun releaseNotes(arguments: GetApplicationRelease.Arguments): List<Release> {
            requested += arguments.repository
            failure?.let { throw it }
            return releases[arguments.repository].orEmpty().mapNotNull { release ->
                val link = config().installerLink(release) ?: return@mapNotNull null
                Release(release.version, release.info, release.releaseLink, link, release.preRelease, release.draft)
            }
        }
    }

    private fun githubRelease(tag: String, vararg assets: String, preRelease: Boolean = false) = GithubRelease(
        version = tag,
        info = "Notes of $tag",
        releaseLink = "https://github.com/komikku-app/komikku/releases/tag/$tag",
        assets = assets.map { GitHubAsset(it, "https://github.com/download/$tag/$it") },
        preRelease = preRelease,
    )

    private fun desktopRelease(tag: String, preRelease: Boolean = false) =
        githubRelease(tag, "Komikku-$tag.apk", "Komikku-${tag.removePrefix("v")}.msi", "komikku_${tag.removePrefix("v")}_amd64.deb", "Komikku-${tag.removePrefix("v")}.dmg", preRelease = preRelease)

    private var clock = 1_000_000_000_000L
    private val store by lazy { DesktopPreferenceStore(File(dir, "prefs.json")) }
    private val schedulerPreferences by lazy { DesktopSchedulerPreferences(store) }

    /** Android's throttle key, read by [GetApplicationRelease]. */
    private fun interactorLastCheck() = store.getLong(Preference.appStateKey("last_app_check"), 0L)

    private fun stable(version: String = "1.13.6", enabled: Boolean = true, os: DesktopOs = DesktopOs.Linux) =
        DesktopUpdaterConfig(enabled = enabled, isPreview = false, versionName = version, commitCount = 0, os = os)

    private fun preview(commitCount: Int, enabled: Boolean = true) =
        DesktopUpdaterConfig(enabled = enabled, isPreview = true, versionName = "1.13.6", commitCount = commitCount, os = DesktopOs.Linux)

    private fun checker(config: DesktopUpdaterConfig): Pair<DesktopAppUpdateChecker, FakeReleases> {
        val fake = FakeReleases { config }
        return DesktopAppUpdateChecker(GetApplicationRelease(fake, store), config, schedulerPreferences, now = { clock }) to fake
    }

    @Test
    fun `a newer stable release with a desktop installer is offered with its release page`() = runTest {
        val (checker, fake) = checker(stable("1.13.6"))
        fake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"), desktopRelease("v1.13.6"))

        val result = checker.check().shouldBeInstanceOf<DesktopAppUpdateChecker.Check.NewUpdate>()

        result.release.version shouldBe "v1.14.0"
        result.release.releaseLink shouldBe "https://github.com/komikku-app/komikku/releases/tag/v1.14.0"
        result.release.downloadLink shouldBe "https://github.com/download/v1.14.0/komikku_1.14.0_amd64.deb"
        result.release.info shouldContain "Notes of v1.14.0"
        checker.available.value shouldBe result.release
        fake.requested shouldBe listOf("komikku-app/komikku")

        checker.dismiss()
        checker.available.value.shouldBeNull()
    }

    @Test
    fun `the same or an older version is no update`() = runTest {
        val (same, sameFake) = checker(stable("1.14.0"))
        sameFake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"), desktopRelease("v1.13.6"))
        same.check(forceCheck = true) shouldBe DesktopAppUpdateChecker.Check.NoNewUpdate

        val (newer, newerFake) = checker(stable("1.15.1"))
        newerFake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"))
        newer.check(forceCheck = true) shouldBe DesktopAppUpdateChecker.Check.NoNewUpdate
        newer.available.value.shouldBeNull()
    }

    @Test
    fun `Android-only releases and pre-releases are skipped`() = runTest {
        val (checker, fake) = checker(stable("1.13.6"))
        fake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(
            githubRelease("v1.16.0", "Komikku-v1.16.0-arm64-v8a.apk", "Komikku-v1.16.0.apk"),
            desktopRelease("v1.15.0", preRelease = true),
            desktopRelease("v1.13.6"),
        )
        checker.check(forceCheck = true) shouldBe DesktopAppUpdateChecker.Check.NoNewUpdate
    }

    @Test
    fun `the preview channel compares commit counts on the preview repository`() = runTest {
        val (checker, fake) = checker(preview(commitCount = 1234))
        fake.releases[DesktopUpdaterConfig.PREVIEW_REPOSITORY] = listOf(desktopRelease("r1240"), desktopRelease("r1234"))

        val result = checker.check().shouldBeInstanceOf<DesktopAppUpdateChecker.Check.NewUpdate>()
        result.release.version shouldBe "r1240"
        fake.requested shouldBe listOf("komikku-app/komikku-preview")

        val (current, currentFake) = checker(preview(commitCount = 1240))
        currentFake.releases[DesktopUpdaterConfig.PREVIEW_REPOSITORY] = listOf(desktopRelease("r1240"))
        current.check(forceCheck = true) shouldBe DesktopAppUpdateChecker.Check.NoNewUpdate

        // A preview build without a commit count has nothing to compare
        val (unknown, unknownFake) = checker(preview(commitCount = 0))
        unknown.check(forceCheck = true) shouldBe DesktopAppUpdateChecker.Check.Disabled
        unknownFake.requested.shouldBeEmpty()
    }

    @Test
    fun `a network failure is reported and does not start the throttle`() = runTest {
        val (checker, fake) = checker(stable("1.13.6"))
        fake.failure = IOException("offline")

        val result = checker.check().shouldBeInstanceOf<DesktopAppUpdateChecker.Check.Failed>()
        result.error.message shouldBe "offline"
        checker.available.value.shouldBeNull()

        // The next automatic check contacts GitHub again
        fake.failure = null
        fake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"))
        checker.check().shouldBeInstanceOf<DesktopAppUpdateChecker.Check.NewUpdate>()
        fake.requested.size shouldBe 2
    }

    @Test
    fun `automatic checks are throttled like Android, a forced check is not`() = runTest {
        val (checker, fake) = checker(stable("1.13.6"))
        fake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"))
        checker.check().shouldBeInstanceOf<DesktopAppUpdateChecker.Check.NewUpdate>()

        checker.check() shouldBe DesktopAppUpdateChecker.Check.NoNewUpdate
        fake.requested.size shouldBe 1

        checker.check(forceCheck = true).shouldBeInstanceOf<DesktopAppUpdateChecker.Check.NewUpdate>()
        fake.requested.size shouldBe 2
    }

    @Test
    fun `with the build flag off or a development version nothing is contacted`() = runTest {
        val (off, offFake) = checker(stable(enabled = false))
        offFake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v9.0.0"))
        off.isEnabled shouldBe false
        off.check(forceCheck = true) shouldBe DesktopAppUpdateChecker.Check.Disabled
        off.checkOnStart(CoroutineScope(Dispatchers.Unconfined)).shouldBeNull()
        offFake.requested.shouldBeEmpty()
        schedulerPreferences.lastAppUpdateCheck().get() shouldBe 0L

        val (dev, devFake) = checker(stable(version = "dev"))
        dev.check(forceCheck = true) shouldBe DesktopAppUpdateChecker.Check.Disabled
        devFake.requested.shouldBeEmpty()

        // Off by default: the build passes no `komikku.updater` property
        System.getProperty("komikku.updater").shouldBeNull()
        DesktopUpdaterConfig.fromSystemProperties().isActive shouldBe false
    }

    @Test
    fun `the scheduled check runs every three days while enabled and counts the start-up check`() = runTest {
        val (checker, fake) = checker(stable("1.13.6"))
        fake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"))
        val task = appUpdateCheckTask(checker, schedulerPreferences, now = { clock })
        task.intervalMillis() shouldBe 3.days.inWholeMilliseconds

        checker.checkOnStart(CoroutineScope(Dispatchers.Unconfined))!!.join()
        task.lastRun() shouldBe clock
        checker.available.value?.version shouldBe "v1.14.0"

        // Within the interactor's two days (its own clock): throttled, nothing contacted, still a run
        clock += 3.days.inWholeMilliseconds
        fake.failure = IOException("offline")
        task.run() shouldBe DesktopScheduler.Outcome.Done
        task.lastRun() shouldBe clock
        fake.requested.size shouldBe 1

        // After the throttle (the interactor's key moved back): contacted, the failure waits a whole interval
        interactorLastCheck().set(0L)
        clock += 3.days.inWholeMilliseconds
        task.run() shouldBe DesktopScheduler.Outcome.Failed
        fake.requested.size shouldBe 2

        val (off, _) = checker(stable(enabled = false))
        appUpdateCheckTask(off, schedulerPreferences).intervalMillis() shouldBe 0L
    }

    @Test
    fun `the installer for the running OS is picked from the release assets`() {
        val release = desktopRelease("v1.14.0")
        stable(os = DesktopOs.Windows).installerLink(release) shouldBe "https://github.com/download/v1.14.0/Komikku-1.14.0.msi"
        stable(os = DesktopOs.MacOs).installerLink(release) shouldBe "https://github.com/download/v1.14.0/Komikku-1.14.0.dmg"
        stable(os = DesktopOs.Linux).installerLink(release) shouldBe "https://github.com/download/v1.14.0/komikku_1.14.0_amd64.deb"
        stable().installerLink(githubRelease("v1.14.0", "Komikku-v1.14.0.apk")).shouldBeNull()

        DesktopOs.current("Windows 11") shouldBe DesktopOs.Windows
        DesktopOs.current("Mac OS X") shouldBe DesktopOs.MacOs
        DesktopOs.current("Linux") shouldBe DesktopOs.Linux
        stable("1.13.6").currentReleaseUrl shouldBe "https://github.com/komikku-app/komikku/releases/tag/v1.13.6"
        preview(1234).currentReleaseUrl shouldBe "https://github.com/komikku-app/komikku-preview/releases/tag/r1234"
    }
}
