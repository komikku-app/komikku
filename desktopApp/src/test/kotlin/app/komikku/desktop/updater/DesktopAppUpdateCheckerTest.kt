package app.komikku.desktop.updater

import app.komikku.desktop.scheduler.DesktopScheduler
import app.komikku.desktop.scheduler.DesktopSchedulerPreferences
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
import kotlin.time.Duration.Companion.minutes

/**
 * P6: the desktop update check on Android's [GetApplicationRelease] with a fake release source shaped like the GitHub
 * releases API (newest first), filtered through the desktop selection (comparable tag, installer) like the app's
 * `ReleaseServiceImpl`.
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
                val link = config().downloadLink(release) ?: return@mapNotNull null
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

    private fun stable(
        version: String = "1.13.6",
        enabled: Boolean = true,
        os: DesktopOs = DesktopOs.Linux,
        arch: DesktopArch? = DesktopArch.X64,
        linuxPackaging: LinuxPackaging = LinuxPackaging.Unknown,
    ) = DesktopUpdaterConfig(
        enabled = enabled,
        isPreview = false,
        versionName = version,
        commitCount = 0,
        os = os,
        arch = arch,
        linuxPackaging = linuxPackaging,
    )

    private fun preview(commitCount: Int, enabled: Boolean = true) = DesktopUpdaterConfig(
        enabled = enabled,
        isPreview = true,
        versionName = "1.13.6",
        commitCount = commitCount,
        os = DesktopOs.Linux,
        arch = DesktopArch.X64,
    )

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
    fun `a network failure is reported, not recorded as a check, and retried after the short delay`() = runTest {
        val (checker, fake) = checker(stable("1.13.6"))
        fake.failure = IOException("offline")

        val result = checker.check().shouldBeInstanceOf<DesktopAppUpdateChecker.Check.Failed>()
        result.error.message shouldBe "offline"
        checker.available.value.shouldBeNull()
        // No answer: the last check time stays unset, so automatic checks are not blocked for days
        schedulerPreferences.lastAppUpdateCheck().get() shouldBe 0L

        // Right after it (e.g. the scheduler's catch-up after the start-up check): the same failure, nothing contacted
        fake.failure = null
        fake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"))
        checker.check() shouldBe result
        fake.requested.size shouldBe 1

        // A forced check always contacts GitHub; after the short delay an automatic one does too
        checker.check(forceCheck = true).shouldBeInstanceOf<DesktopAppUpdateChecker.Check.NewUpdate>()
        fake.requested.size shouldBe 2
        schedulerPreferences.lastAppUpdateCheck().get() shouldBe clock

        // (The throttles of the found update moved back)
        schedulerPreferences.lastAppUpdateCheck().set(0L)
        interactorLastCheck().set(0L)
        val (later, laterFake) = checker(stable("1.13.6"))
        laterFake.failure = IOException("offline")
        later.check().shouldBeInstanceOf<DesktopAppUpdateChecker.Check.Failed>()
        clock += DesktopAppUpdateChecker.FAILURE_RETRY_DELAY.inWholeMilliseconds
        laterFake.failure = null
        laterFake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"))
        later.check().shouldBeInstanceOf<DesktopAppUpdateChecker.Check.NewUpdate>()
        laterFake.requested.size shouldBe 2
    }

    @Test
    fun `an up-to-date app does not ask GitHub at every start`() = runTest {
        val (checker, fake) = checker(stable("1.14.0"))
        fake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"))

        checker.checkOnStart(CoroutineScope(Dispatchers.Unconfined))!!.join()
        fake.requested.size shouldBe 1
        schedulerPreferences.lastAppUpdateCheck().get() shouldBe clock
        // The interactor's own throttle did not start (no update found)
        interactorLastCheck().get() shouldBe 0L

        // The next start a day later: not contacted
        clock += 1.days.inWholeMilliseconds
        val (nextStart, nextFake) = checker(stable("1.14.0"))
        nextFake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"))
        nextStart.checkOnStart(CoroutineScope(Dispatchers.Unconfined))!!.join()
        nextFake.requested.shouldBeEmpty()
        nextStart.check() shouldBe DesktopAppUpdateChecker.Check.Throttled

        // Two days after the last answer: contacted again
        clock += 1.days.inWholeMilliseconds
        nextStart.check() shouldBe DesktopAppUpdateChecker.Check.NoNewUpdate
        nextFake.requested.size shouldBe 1
    }

    @Test
    fun `automatic checks are throttled like Android, a forced check is not`() = runTest {
        val (checker, fake) = checker(stable("1.13.6"))
        fake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"))
        checker.check().shouldBeInstanceOf<DesktopAppUpdateChecker.Check.NewUpdate>()

        checker.check() shouldBe DesktopAppUpdateChecker.Check.Throttled
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
        val task = appUpdateCheckTask(checker, schedulerPreferences)
        task.intervalMillis() shouldBe 3.days.inWholeMilliseconds
        task.retryDelay shouldBe DesktopAppUpdateChecker.FAILURE_RETRY_DELAY

        checker.checkOnStart(CoroutineScope(Dispatchers.Unconfined))!!.join()
        task.lastRun() shouldBe clock
        checker.available.value?.version shouldBe "v1.14.0"

        // Within the interactor's two days (its own clock, it found an update): an answer without contacting GitHub,
        // recorded as a run
        clock += 3.days.inWholeMilliseconds
        fake.failure = IOException("offline")
        task.run() shouldBe DesktopScheduler.Outcome.Done
        task.lastRun() shouldBe clock
        fake.requested.size shouldBe 1

        // After the throttles (the interactor's key moved back): contacted, the failure is not a run and retries soon
        interactorLastCheck().set(0L)
        val lastAnswer = clock
        clock += 3.days.inWholeMilliseconds
        task.run() shouldBe DesktopScheduler.Outcome.Busy
        fake.requested.size shouldBe 2
        task.lastRun() shouldBe lastAnswer

        val (off, _) = checker(stable(enabled = false))
        appUpdateCheckTask(off, schedulerPreferences).intervalMillis() shouldBe 0L
    }

    @Test
    fun `an offline start does not block the scheduled check for days`() = runBlocking<Unit> {
        val (checker, fake) = checker(stable("1.13.6"))
        fake.failure = IOException("offline")
        val scheduler = DesktopScheduler(
            scope = CoroutineScope(Dispatchers.Unconfined),
            tasks = listOf(appUpdateCheckTask(checker, schedulerPreferences)),
            now = { clock },
        )

        // The start-up check fails, then the scheduler's catch-up: no second request, a short retry
        checker.checkOnStart(CoroutineScope(Dispatchers.Unconfined))!!.join()
        scheduler.checkNow() shouldBe listOf(APP_UPDATE_CHECK_TASK)
        withTimeout(10_000) { scheduler.awaitRunning() }
        fake.requested.size shouldBe 1
        schedulerPreferences.lastAppUpdateCheck().get() shouldBe 0L

        // Back online: the retry 10 minutes later finds the update
        fake.failure = null
        fake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(desktopRelease("v1.14.0"))
        clock += 5.minutes.inWholeMilliseconds
        scheduler.checkNow().shouldBeEmpty()
        clock += 5.minutes.inWholeMilliseconds
        scheduler.checkNow() shouldBe listOf(APP_UPDATE_CHECK_TASK)
        withTimeout(10_000) { scheduler.awaitRunning() }
        fake.requested.size shouldBe 2
        checker.available.value?.version shouldBe "v1.14.0"
        schedulerPreferences.lastAppUpdateCheck().get() shouldBe clock
    }

    @Test
    fun `a release with a tag that cannot be compared is skipped, not failing the check`() = runTest {
        val (checker, fake) = checker(stable("1.13.6"))
        fake.releases[DesktopUpdaterConfig.STABLE_REPOSITORY] = listOf(
            desktopRelease("desktop-v2"),
            desktopRelease("v1.15"),
            desktopRelease("nightly"),
            desktopRelease("v1.14.0"),
        )
        val result = checker.check(forceCheck = true).shouldBeInstanceOf<DesktopAppUpdateChecker.Check.NewUpdate>()
        result.release.version shouldBe "v1.14.0"

        val config = stable("1.13.6")
        config.isComparableTag("v1.14.0") shouldBe true
        config.isComparableTag("v1.14.0.1") shouldBe true
        config.isComparableTag("v1.15") shouldBe false
        config.isComparableTag("1.x.0") shouldBe false
        config.isComparableTag("v1..0") shouldBe false
        val preview = preview(1234)
        preview.isComparableTag("r1240") shouldBe true
        preview.isComparableTag("r1.2") shouldBe false
        preview.isComparableTag("r99999999999") shouldBe false

        val (previewChecker, previewFake) = checker(preview)
        previewFake.releases[DesktopUpdaterConfig.PREVIEW_REPOSITORY] = listOf(desktopRelease("r12.4"), desktopRelease("r1240"))
        previewChecker.check(forceCheck = true).shouldBeInstanceOf<DesktopAppUpdateChecker.Check.NewUpdate>()
            .release.version shouldBe "r1240"
    }

    @Test
    fun `the installer for the running OS is picked from the release assets`() {
        val release = desktopRelease("v1.14.0")
        stable(os = DesktopOs.Windows).installerLink(release) shouldBe "https://github.com/download/v1.14.0/Komikku-1.14.0.msi"
        stable(os = DesktopOs.MacOs).installerLink(release) shouldBe "https://github.com/download/v1.14.0/Komikku-1.14.0.dmg"
        stable(os = DesktopOs.Linux).installerLink(release) shouldBe "https://github.com/download/v1.14.0/komikku_1.14.0_amd64.deb"
        stable().installerLink(githubRelease("v1.14.0", "Komikku-v1.14.0.apk")).shouldBeNull()

        // Neither format preferred by an unknown distribution: the first Linux asset wins
        stable(os = DesktopOs.Linux).installerLink(githubRelease("v1.14.0", "komikku-1.14.0.x86_64.rpm", "komikku_1.14.0_amd64.deb")) shouldBe
            "https://github.com/download/v1.14.0/komikku-1.14.0.x86_64.rpm"

        DesktopOs.current("Windows 11") shouldBe DesktopOs.Windows
        DesktopOs.current("Mac OS X") shouldBe DesktopOs.MacOs
        DesktopOs.current("Linux") shouldBe DesktopOs.Linux
        stable("1.13.6").currentReleaseUrl shouldBe "https://github.com/komikku-app/komikku/releases/tag/v1.13.6"
        preview(1234).currentReleaseUrl shouldBe "https://github.com/komikku-app/komikku-preview/releases/tag/r1234"
    }

    @Test
    fun `the release pipeline's asset names are matched per OS and architecture`() {
        // What build_desktop.yml attaches (.github/scripts/desktop_release_assets.sh, BUILDING.md "Release pipeline"),
        // next to the Android release's own APKs (build_release.yml) and the checksum files.
        val pipeline = listOf(
            "Komikku-1.14.0-linux-x64.deb",
            "Komikku-1.14.0-linux-x64.rpm",
            "Komikku-1.14.0-linux-x64.tar.gz",
            "Komikku-1.14.0-linux-x64-SHA256SUMS.txt",
            "Komikku-1.14.0-windows-x64.msi",
            "Komikku-1.14.0-windows-x64.exe",
            "Komikku-1.14.0-windows-x64-SHA256SUMS.txt",
            "Komikku-1.14.0-macos-arm64.dmg",
            "Komikku-1.14.0-macos-arm64-SHA256SUMS.txt",
            "Komikku-1.14.0-macos-x64.dmg",
            "Komikku-1.14.0-macos-x64-SHA256SUMS.txt",
            "Komikku-1.14.0-desktop-SHA256SUMS.txt",
        )
        val android = listOf(
            "Komikku-v1.14.0.apk",
            "Komikku-arm64-v8a-v1.14.0.apk",
            "Komikku-armeabi-v7a-v1.14.0.apk",
            "Komikku-x86-v1.14.0.apk",
            "Komikku-x86_64-v1.14.0.apk",
        )
        val release = githubRelease("v1.14.0", *(android + pipeline).toTypedArray())
        fun link(os: DesktopOs, arch: DesktopArch?) = stable(os = os, arch = arch).downloadLink(release)?.substringAfterLast('/')

        link(DesktopOs.Windows, DesktopArch.X64) shouldBe "Komikku-1.14.0-windows-x64.msi"
        link(DesktopOs.MacOs, DesktopArch.Arm64) shouldBe "Komikku-1.14.0-macos-arm64.dmg"
        link(DesktopOs.MacOs, DesktopArch.X64) shouldBe "Komikku-1.14.0-macos-x64.dmg"
        // An unknown distribution gets the app image that runs anywhere
        link(DesktopOs.Linux, DesktopArch.X64) shouldBe "Komikku-1.14.0-linux-x64.tar.gz"
        // Not built: no update offered rather than another architecture's installer.
        link(DesktopOs.Windows, DesktopArch.Arm64).shouldBeNull()
        link(DesktopOs.Linux, DesktopArch.Arm64).shouldBeNull()
        // Each format on its own counts, the packed Linux app image included; checksums and APKs never do.
        listOf("deb", "rpm", "tar.gz").forEach { ext ->
            stable(os = DesktopOs.Linux).installerLink(githubRelease("v1.14.0", "Komikku-1.14.0-linux-x64.$ext"))
                .shouldNotBeNull()
        }
        stable(os = DesktopOs.Windows).installerLink(githubRelease("v1.14.0", "Komikku-1.14.0-windows-x64.exe")).shouldNotBeNull()
        DesktopOs.entries.forEach { os ->
            stable(os = os).installerLink(githubRelease("v1.14.0", *(android + pipeline.filter { it.endsWith(".txt") }).toTypedArray()))
                .shouldBeNull()
        }
        pipeline.filterNot { it.endsWith(".txt") }.forEach { name ->
            DesktopArch.of(name) shouldBe if ("-arm64." in name) DesktopArch.Arm64 else DesktopArch.X64
        }
    }

    @Test
    fun `the installer matches the CPU architecture when the asset name states one`() {
        val release = githubRelease(
            "v1.14.0",
            "Komikku-1.14.0-arm64.dmg",
            "Komikku-1.14.0-x64.dmg",
            "komikku_1.14.0_arm64.deb",
            "komikku_1.14.0_amd64.deb",
            "Komikku-1.14.0-aarch64.msi",
            "Komikku-1.14.0-x86_64.msi",
        )
        fun link(os: DesktopOs, arch: DesktopArch?) = stable(os = os, arch = arch).installerLink(release)?.substringAfterLast('/')
        link(DesktopOs.MacOs, DesktopArch.Arm64) shouldBe "Komikku-1.14.0-arm64.dmg"
        link(DesktopOs.MacOs, DesktopArch.X64) shouldBe "Komikku-1.14.0-x64.dmg"
        link(DesktopOs.Linux, DesktopArch.Arm64) shouldBe "komikku_1.14.0_arm64.deb"
        link(DesktopOs.Linux, DesktopArch.X64) shouldBe "komikku_1.14.0_amd64.deb"
        link(DesktopOs.Windows, DesktopArch.Arm64) shouldBe "Komikku-1.14.0-aarch64.msi"
        link(DesktopOs.Windows, DesktopArch.X64) shouldBe "Komikku-1.14.0-x86_64.msi"
        // Unknown architecture of this JVM: only the OS counts
        link(DesktopOs.MacOs, null) shouldBe "Komikku-1.14.0-arm64.dmg"

        // Only another architecture: no installer for this build; no architecture in the name: counts for any
        stable(os = DesktopOs.MacOs, arch = DesktopArch.X64).installerLink(githubRelease("v1.14.0", "Komikku-1.14.0-ARM64.dmg")).shouldBeNull()
        stable(os = DesktopOs.MacOs, arch = DesktopArch.Arm64).installerLink(githubRelease("v1.14.0", "Komikku-1.14.0.dmg")) shouldBe
            "https://github.com/download/v1.14.0/Komikku-1.14.0.dmg"
        // Part of a longer word is no architecture
        DesktopArch.of("Komikku-x640.dmg").shouldBeNull()
        DesktopArch.of("komikku-1.14.0.x86-64.rpm") shouldBe DesktopArch.X64

        DesktopArch.current("amd64") shouldBe DesktopArch.X64
        DesktopArch.current("x86_64") shouldBe DesktopArch.X64
        DesktopArch.current("aarch64") shouldBe DesktopArch.Arm64
        DesktopArch.current("ppc64le").shouldBeNull()
    }

    @Test
    fun `a Linux distribution gets its own package format`() {
        val release = githubRelease(
            "v1.14.0",
            "Komikku-1.14.0-linux-x64.deb",
            "Komikku-1.14.0-linux-x64.rpm",
            "Komikku-1.14.0-linux-x64.tar.gz",
        )
        fun link(packaging: LinuxPackaging, release: GithubRelease) =
            stable(os = DesktopOs.Linux, linuxPackaging = packaging).installerLink(release)?.substringAfterLast('/')

        link(LinuxPackaging.Rpm, release) shouldBe "Komikku-1.14.0-linux-x64.rpm"
        link(LinuxPackaging.Deb, release) shouldBe "Komikku-1.14.0-linux-x64.deb"
        link(LinuxPackaging.Unknown, release) shouldBe "Komikku-1.14.0-linux-x64.tar.gz"
        // Without its format: the portable app image, else whatever there is
        link(LinuxPackaging.Rpm, githubRelease("v1.14.0", "Komikku-1.14.0-linux-x64.deb", "Komikku-1.14.0-linux-x64.tar.gz")) shouldBe
            "Komikku-1.14.0-linux-x64.tar.gz"
        link(LinuxPackaging.Rpm, githubRelease("v1.14.0", "Komikku-1.14.0-linux-x64.deb")) shouldBe "Komikku-1.14.0-linux-x64.deb"
        // The format only counts on Linux
        stable(os = DesktopOs.Windows, linuxPackaging = LinuxPackaging.Rpm)
            .installerLink(githubRelease("v1.14.0", "Komikku-1.14.0-windows-x64.msi", "Komikku-1.14.0-linux-x64.rpm"))
            ?.substringAfterLast('/') shouldBe "Komikku-1.14.0-windows-x64.msi"
    }

    @Test
    fun `the package format comes from os-release`() {
        LinuxPackaging.of("NAME=\"Fedora Linux\"\nID=fedora\nVERSION_ID=41\n") shouldBe LinuxPackaging.Rpm
        LinuxPackaging.of("ID=\"rocky\"\nID_LIKE=\"rhel centos fedora\"\n") shouldBe LinuxPackaging.Rpm
        LinuxPackaging.of("ID=\"opensuse-tumbleweed\"\nID_LIKE=\"opensuse suse\"\n") shouldBe LinuxPackaging.Rpm
        LinuxPackaging.of("ID=ubuntu\nID_LIKE=debian\n") shouldBe LinuxPackaging.Deb
        LinuxPackaging.of("ID=linuxmint\nID_LIKE=\"ubuntu debian\"\n") shouldBe LinuxPackaging.Deb
        LinuxPackaging.of("ID=arch\n") shouldBe LinuxPackaging.Unknown
        LinuxPackaging.of("") shouldBe LinuxPackaging.Unknown
    }

    @Test
    fun `this system's package format is read from the first os-release file there is`(@TempDir folder: File) {
        val etc = File(folder, "etc-os-release")
        val usrLib = File(folder, "usr-lib-os-release").apply { writeText("ID=fedora\n") }
        LinuxPackaging.current(listOf(etc, usrLib)) shouldBe LinuxPackaging.Rpm
        etc.writeText("ID=debian\n")
        LinuxPackaging.current(listOf(etc, usrLib)) shouldBe LinuxPackaging.Deb
        LinuxPackaging.current(listOf(File(folder, "missing"))) shouldBe LinuxPackaging.Unknown
    }

    @Test
    fun `an offered RPM comes with its install commands and the release notes' Linux section`() {
        val assets = DesktopReleaseAssets()
        fun offer(packaging: LinuxPackaging, vararg names: String): RpmInstall? {
            val github = githubRelease("v1.14.0", *names)
            val link = assets.selector(stable(linuxPackaging = packaging))(github) ?: return null
            val release = Release(github.version, github.info, github.releaseLink, link)
            return RpmInstall.of(release, assets.namesOf(release))
        }
        val rpm = "Komikku-1.14.0-linux-x64.rpm"
        val deb = "Komikku-1.14.0-linux-x64.deb"

        val unsigned = offer(LinuxPackaging.Rpm, rpm, deb).shouldNotBeNull()
        unsigned.signingKey.shouldBeNull()
        unsigned.instructionsUrl shouldBe "https://github.com/komikku-app/komikku/releases/tag/v1.14.0#linux"
        unsigned.commands shouldBe listOf(
            "sudo dnf install ./Komikku-1.14.0-linux-x64.rpm",
            "sudo rpm -i --nosignature ./Komikku-1.14.0-linux-x64.rpm",
        )

        val signed = offer(LinuxPackaging.Rpm, rpm, "Komikku-1.14.0-linux-x64-rpm-signing-key.asc").shouldNotBeNull()
        signed.signingKey shouldBe "Komikku-1.14.0-linux-x64-rpm-signing-key.asc"
        signed.commands shouldBe listOf(
            "sudo rpm --import ./Komikku-1.14.0-linux-x64-rpm-signing-key.asc",
            "sudo dnf install ./Komikku-1.14.0-linux-x64.rpm",
        )
        // Another architecture's key does not sign this package
        offer(LinuxPackaging.Rpm, rpm, "Komikku-1.14.0-linux-arm64-rpm-signing-key.asc")?.signingKey.shouldBeNull()

        // A .deb or another OS's installer offered: no RPM note
        offer(LinuxPackaging.Deb, rpm, deb).shouldBeNull()
        RpmInstall.of(Release("v1.14.0", "", "https://github.com/x", "https://github.com/download/v1.14.0/Komikku-1.14.0-windows-x64.msi"), emptyList())
            .shouldBeNull()
        // A release the check did not select has no recorded names
        assets.namesOf(Release("v1.14.0", "", "https://github.com/x", "https://github.com/download/other.rpm")).shouldBeEmpty()
    }
}
