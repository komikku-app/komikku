package tachiyomi.domain.release.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import java.time.Instant

class GetApplicationReleaseTest {

    private lateinit var getApplicationRelease: GetApplicationRelease
    private lateinit var releaseService: ReleaseService
    private lateinit var preference: Preference<Long>

    @BeforeEach
    fun beforeEach() {
        val preferenceStore = mockk<PreferenceStore>()
        preference = mockk()
        every { preferenceStore.getLong(any(), any()) } returns preference
        releaseService = mockk()

        getApplicationRelease = GetApplicationRelease(releaseService, preferenceStore)
    }

    @Test
    fun `When has update but is third party expect third party installation`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val releases = listOf(
            Release(
                "v2.0.0",
                "info",
                "http://example.com/release_link",
                listOf("http://example.com/assets"),
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                isThirdParty = true,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.ThirdPartyInstallation
    }

    @Test
    fun `When has update but is preview expect new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val releases = listOf(
            Release(
                "r2000",
                "info",
                "http://example.com/release_link",
                listOf("http://example.com/assets"),
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = true,
                isThirdParty = false,
                commitCount = 1000,
                versionName = "",
                repository = "test",
            ),
        )

        // KMK: Don't cast, will throw exception if the result is different from expected
        result shouldBe GetApplicationRelease.Result.NewUpdate(releases.getLatest()!!)
    }

    @Test
    fun `When has update expect new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val releases =
            listOf(
                Release(
                    "v2.0.0",
                    "info",
                    "http://example.com/release_link",
                    listOf("http://example.com/assets"),
                ),
            )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                isThirdParty = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
            ),
        )

        // KMK: Don't cast, will throw exception if the result is different from expected
        result shouldBe GetApplicationRelease.Result.NewUpdate(releases.getLatest()!!)
    }

    @Test
    fun `When has no update expect no new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val releases = listOf(
            Release(
                "v1.0.0",
                "info",
                "http://example.com/release_link",
                listOf("http://example.com/assets"),
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                isThirdParty = false,
                commitCount = 0,
                versionName = "v2.0.0",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When now is before three days expect no new update`() = runTest {
        every { preference.get() } returns Instant.now().toEpochMilli()
        every { preference.set(any()) }.answers { }

        val releases = listOf(
            Release(
                "v2.0.0",
                "info",
                "http://example.com/release_link",
                listOf("http://example.com/assets"),
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                isThirdParty = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
            ),
        )

        coVerify(exactly = 0) { releaseService.latest(any()) }
        coVerify(exactly = 0) { releaseService.releaseNotes(any()) }
        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    // KMK -->
    // TODO: remove below tests when all users finished switching from preview to stable
    @Test
    fun `When preview version updating to new stable version with preview tag expect new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val releases = listOf(
            Release(
                "r1234",
                "info",
                "http://example.com/release_link",
                listOf("http://example.com/assets"),
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = true,
                isThirdParty = false,
                commitCount = 25,
                versionName = "",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NewUpdate(releases.getLatest()!!)
    }

    @Test
    fun `When has stable update with preview version but current app is stable version expect no update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val releases = listOf(
            Release(
                "r1234",
                "info",
                "http://example.com/release_link",
                listOf("http://example.com/assets"),
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                isThirdParty = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When has stable update but current app is preview version (same repo) expect new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val releases = listOf(
            Release(
                "v2.0.0",
                "info",
                "http://example.com/release_link",
                listOf("http://example.com/assets"),
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = true,
                isThirdParty = false,
                commitCount = 25,
                versionName = "",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NewUpdate(releases.getLatest()!!)
    }
    // KMK <--
}
