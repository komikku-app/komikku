package app.komikku.desktop.security

import app.komikku.desktop.di.DesktopInjekt
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.base.delegate.AppLockRules
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Calendar

/**
 * P16: the desktop app lock's rules — on only with "Require unlock" and a PIN / password, locked at start, the secret
 * kept as a hash outside backups, wrong entries throttled, Android's "Lock when idle" after the window was minimised and
 * SY's lock days / times through the shared [AppLockRules].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesktopAppLockTest {

    private lateinit var store: PreferenceStore
    private lateinit var security: SecurityPreferences
    private var now = 1_000_000L

    @BeforeAll
    fun boot(@TempDir dir: File) {
        DesktopInjekt.init(dir)
        store = Injekt.get()
        security = Injekt.get()
    }

    @BeforeEach
    fun reset() {
        security.useAuthenticator().delete()
        security.lockAppAfter().delete()
        security.lastAppClosed().delete()
        security.authenticatorTimeRanges().delete()
        security.authenticatorDays().delete()
        store.getString(Preference.appStateKey(DesktopAppLock.SECRET_KEY), "").delete()
        now = 1_000_000L
    }

    private fun newLock(calendar: Calendar = Calendar.getInstance()) = DesktopAppLock(security, store, clock = { now }, calendar = { calendar })

    @Test
    fun `the lock is on with a secret, starts locked and unlocks with it only`() = runBlocking<Unit> {
        newLock().run {
            enabled.value shouldBe false
            locked.value shouldBe false
            enable("1234")
            enabled.value shouldBe true
            // Switching it on does not lock the running app
            locked.value shouldBe false
        }
        security.useAuthenticator().get() shouldBe true
        // Only a hash is stored, under an app-state key (never in backups)
        val stored = store.getString(Preference.appStateKey(DesktopAppLock.SECRET_KEY), "").get()
        stored shouldStartWith "pbkdf2-sha256:"
        stored shouldNotContain "1234"
        Preference.isAppState(Preference.appStateKey(DesktopAppLock.SECRET_KEY)) shouldBe true

        // A new start is locked
        val lock = newLock()
        lock.locked.value shouldBe true
        lock.unlock("4321") shouldBe DesktopAppLock.Attempt.Wrong
        lock.locked.value shouldBe true
        lock.unlock("1234") shouldBe DesktopAppLock.Attempt.Accepted
        lock.locked.value shouldBe false

        // Change and turn off need the current secret
        lock.change("nope", "abcd") shouldBe DesktopAppLock.Attempt.Wrong
        lock.change("1234", "abcd") shouldBe DesktopAppLock.Attempt.Accepted
        lock.disable("1234") shouldBe DesktopAppLock.Attempt.Wrong
        lock.disable("abcd") shouldBe DesktopAppLock.Attempt.Accepted
        lock.enabled.value shouldBe false
        security.useAuthenticator().get() shouldBe false
        newLock().locked.value shouldBe false
    }

    @Test
    fun `a restored require-unlock without a secret is switched off`() {
        security.useAuthenticator().set(true)
        val lock = newLock()
        lock.enabled.value shouldBe false
        lock.locked.value shouldBe false
        security.useAuthenticator().get() shouldBe false
        lock.lockNow()
        lock.locked.value shouldBe false
    }

    @Test
    fun `wrong entries in a row make the next tries wait`() = runBlocking<Unit> {
        newLock().enable("1234")
        val lock = newLock()
        repeat(DesktopAppLock.FREE_ATTEMPTS) { lock.unlock("0000") shouldBe DesktopAppLock.Attempt.Wrong }
        // Even the right one waits now
        lock.unlock("1234").shouldBeInstanceOf<DesktopAppLock.Attempt.Blocked>().seconds shouldBe 30
        now += 30_000
        lock.unlock("0000") shouldBe DesktopAppLock.Attempt.Wrong
        // The wait grows with each further failure
        lock.unlock("1234").shouldBeInstanceOf<DesktopAppLock.Attempt.Blocked>().seconds shouldBe 60
        now += 60_000
        lock.unlock("1234") shouldBe DesktopAppLock.Attempt.Accepted
        // A success starts the count again
        lock.lockNow()
        lock.unlock("0000") shouldBe DesktopAppLock.Attempt.Wrong
        lock.unlock("1234") shouldBe DesktopAppLock.Attempt.Accepted
    }

    @Test
    fun `lock when idle follows Android's rule after the window was minimised`() = runBlocking<Unit> {
        newLock().enable("1234")
        val lock = newLock()
        lock.unlock("1234")

        // Never
        security.lockAppAfter().set(-1)
        lock.onWindowHidden()
        now += 60 * 60_000
        lock.onWindowShown()
        lock.locked.value shouldBe false

        // After 2 minutes: not after one, but after two
        security.lockAppAfter().set(2)
        lock.onWindowHidden()
        security.lastAppClosed().get() shouldBe now
        now += 60_000
        lock.onWindowShown()
        lock.locked.value shouldBe false
        security.lastAppClosed().isSet() shouldBe false
        lock.onWindowHidden()
        now += 2 * 60_000
        lock.onWindowShown()
        lock.locked.value shouldBe true
        lock.unlock("1234")

        // Always, but only after the window was hidden (a lone "shown" does not lock)
        security.lockAppAfter().set(0)
        lock.onWindowShown()
        lock.locked.value shouldBe false
        lock.onWindowHidden()
        lock.onWindowShown()
        lock.locked.value shouldBe true
    }

    @Test
    fun `SY's lock days and times decide whether an idle lock happens`() = runBlocking<Unit> {
        newLock().enable("1234")
        // A Monday, 10:30
        val monday = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 23, 10, 30) }
        monday.get(Calendar.DAY_OF_WEEK) shouldBe Calendar.MONDAY
        security.lockAppAfter().set(0)

        // Not on Mondays
        security.authenticatorDays().set(AppLockRules.LOCK_ALL_DAYS and AppLockRules.LOCK_MONDAY.inv())
        newLock(monday).run {
            unlock("1234")
            onWindowHidden()
            onWindowShown()
            locked.value shouldBe false
        }
        // Mondays, but only from 11:00 to 12:00
        security.authenticatorDays().set(AppLockRules.LOCK_ALL_DAYS)
        security.authenticatorTimeRanges().set(setOf("660,720"))
        newLock(monday).run {
            unlock("1234")
            onWindowHidden()
            onWindowShown()
            locked.value shouldBe false
        }
        // 10:00 to 11:00 includes 10:30
        security.authenticatorTimeRanges().set(setOf("600,660", "not a range"))
        newLock(monday).run {
            unlock("1234")
            onWindowHidden()
            onWindowShown()
            locked.value shouldBe true
        }
        AppLockRules.isLockDue(lockAppAfter = 5, lastClosed = 0, nowMillis = 5 * 60_000 - 1) shouldBe false
        AppLockRules.isLockDue(lockAppAfter = 5, lastClosed = 0, nowMillis = 5 * 60_000) shouldBe true
    }
}
