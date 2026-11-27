package tachiyomi.domain.vault

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class VaultSessionTest {

    @AfterEach
    fun reset() {
        VaultSession.init(active = false)
    }

    @Test
    fun `id based preference keys are isolated per profile`() {
        VaultSession.init(active = false)
        VaultSession.profileKey("default_category") shouldBe "default_category"
        VaultSession.isVaultKey("default_category") shouldBe false

        VaultSession.init(active = true)
        VaultSession.profileKey("default_category") shouldBe "default_category__vault"
        VaultSession.isVaultKey("default_category__vault") shouldBe true
    }

    @Test
    fun `short background keeps the vault unlocked`() {
        VaultSession.init(active = true)
        VaultSession.onAuthenticated()

        VaultSession.onAppBackgrounded(now = 1_000L)
        VaultSession.shouldEndSessionOnForeground(now = 20_000L) shouldBe false

        VaultSession.onAppForegrounded()
        VaultSession.shouldEndSessionOnForeground(now = 40_000L) shouldBe false
        VaultSession.requiresAuth.value shouldBe false
    }

    @Test
    fun `background of 30 seconds or more ends the session`() {
        VaultSession.init(active = true)
        VaultSession.onAuthenticated()

        VaultSession.onAppBackgrounded(now = 1_000L)
        VaultSession.shouldEndSessionOnForeground(now = 30_999L) shouldBe false
        VaultSession.shouldEndSessionOnForeground(now = 31_000L) shouldBe true
    }

    @Test
    fun `screen off ends the session`() {
        VaultSession.init(active = true)
        VaultSession.onScreenOff()
        VaultSession.shouldEndSessionOnForeground(now = 0L) shouldBe true

        VaultSession.onAppForegrounded()
        VaultSession.shouldEndSessionOnForeground(now = 0L) shouldBe false
    }

    @Test
    fun `foregrounding resets the background timer`() {
        VaultSession.init(active = true)
        VaultSession.onAppBackgrounded(now = 1_000L)
        VaultSession.onAppForegrounded()
        VaultSession.shouldEndSessionOnForeground(now = 100_000L) shouldBe false
    }

    @Test
    fun `standard profile never expires`() {
        VaultSession.init(active = false)
        VaultSession.onAppBackgrounded(now = 0L)
        VaultSession.onScreenOff()
        VaultSession.shouldEndSessionOnForeground(now = 100_000L) shouldBe false
    }

    @Test
    fun `backgrounding while authenticating does not start the timer`() {
        VaultSession.init(active = true)
        VaultSession.onAuthenticating()
        VaultSession.onAppBackgrounded(now = 1_000L)
        VaultSession.shouldEndSessionOnForeground(now = 100_000L) shouldBe false

        VaultSession.onAuthenticated()
        VaultSession.onAppBackgrounded(now = 101_000L)
        VaultSession.shouldEndSessionOnForeground(now = 130_999L) shouldBe false
        VaultSession.shouldEndSessionOnForeground(now = 131_000L) shouldBe true
    }

    @Test
    fun `authentication state survives a short background`() {
        VaultSession.init(active = true)
        VaultSession.requiresAuth.value shouldBe true

        VaultSession.onAuthenticated()
        VaultSession.requiresAuth.value shouldBe false

        VaultSession.onAppBackgrounded(now = 1_000L)
        VaultSession.requiresAuth.value shouldBe false

        VaultSession.onAuthenticationDismissed()
        VaultSession.requiresAuth.value shouldBe true
    }

    @Test
    fun `session ending flag is reset on init`() {
        VaultSession.init(active = true)
        VaultSession.isSessionEnding shouldBe false

        VaultSession.markSessionEnding()
        VaultSession.isSessionEnding shouldBe true

        VaultSession.init(active = true)
        VaultSession.isSessionEnding shouldBe false
    }
}
