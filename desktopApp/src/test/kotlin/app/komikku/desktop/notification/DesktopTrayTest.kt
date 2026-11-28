package app.komikku.desktop.notification

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.Image

/** ANSWER 011 (b): the temporary tray icon that shows notifications while the tray setting is off. */
class DesktopTrayTest {

    private val backend = FakeTrayBackend()
    private val timer = FakeTrayTimer()
    private val exitHooks = mutableListOf<() -> Unit>()

    private fun tray(temporaryIcons: Boolean = true) =
        DesktopTray(macOs = false, temporaryIcons, backend, timer, hookExit = { exitHooks += it })

    private fun note(title: String) = DesktopNotification(DesktopNotification.Channel.NewChapters, title, "text")

    private fun DesktopTray.installIcon(onOpen: () -> Unit = {}) = install("Open", "Exit", onOpen = onOpen, onExit = {})

    @Test
    fun `without the user's icon a notification adds a temporary icon, shows it and removes it after the wait`() {
        val tray = tray()

        tray.show(note("New chapters")) shouldBe true

        backend.events shouldBe listOf("add temporary", "show temporary: New chapters")
        tray.hasTemporaryIcon shouldBe true
        tray.isInstalled shouldBe false
        timer.scheduled.single().delayMillis shouldBe DesktopTray.TEMPORARY_ICON_MILLIS
        exitHooks shouldHaveSize 1

        timer.elapse()

        backend.events.last() shouldBe "remove temporary"
        backend.alive.shouldBeEmpty()
        tray.hasTemporaryIcon shouldBe false
    }

    @Test
    fun `a second notification during the wait reuses the icon and restarts the wait`() {
        val tray = tray()

        tray.show(note("First")) shouldBe true
        tray.show(note("Second")) shouldBe true

        backend.events shouldBe listOf("add temporary", "show temporary: First", "show temporary: Second")
        timer.scheduled shouldHaveSize 2
        timer.scheduled[0].cancelled shouldBe true
        // The first wait's removal running anyway (cancelled too late) leaves the icon to the second wait
        timer.scheduled[0].task()
        backend.alive shouldHaveSize 1

        timer.elapse(timer.scheduled[1])

        backend.alive.shouldBeEmpty()
        backend.maxAlive shouldBe 1

        // A later notification adds a new icon; the exit is hooked once
        tray.show(note("Later")) shouldBe true
        backend.events.takeLast(2) shouldBe listOf("add temporary", "show temporary: Later")
        exitHooks shouldHaveSize 1
    }

    @Test
    fun `switching the setting on during the wait removes the temporary icon before adding the user's`() {
        val tray = tray()
        tray.show(note("First")) shouldBe true

        tray.installIcon() shouldBe true

        backend.events shouldBe listOf("add temporary", "show temporary: First", "remove temporary", "add user")
        backend.maxAlive shouldBe 1
        tray.hasTemporaryIcon shouldBe false
        timer.scheduled.single().cancelled shouldBe true

        // The old wait ending changes nothing; the next notification goes through the user's icon
        timer.scheduled.single().task()
        tray.show(note("Second")) shouldBe true

        backend.events.drop(4) shouldBe listOf("show user: Second")
        backend.alive.map { it.kind } shouldBe listOf("user")
        timer.scheduled shouldHaveSize 1
    }

    @Test
    fun `the app's exit removes the temporary icon and adds none after`() {
        val tray = tray()
        tray.show(note("First")) shouldBe true

        exitHooks.single().invoke()

        backend.events.last() shouldBe "remove temporary"
        backend.alive.shouldBeEmpty()
        timer.scheduled.single().cancelled shouldBe true
        tray.show(note("After exit")) shouldBe false
        tray.installIcon() shouldBe false
        backend.events.count { it.startsWith("add") } shouldBe 1
    }

    @Test
    fun `removing the icons (window gone, setting off) also removes the temporary icon`() {
        val tray = tray()
        tray.installIcon() shouldBe true
        tray.remove()
        tray.show(note("Off")) shouldBe true

        tray.remove()

        backend.events shouldBe listOf("add user", "remove user", "add temporary", "show temporary: Off", "remove temporary")
        backend.alive.shouldBeEmpty()
        timer.scheduled.single().cancelled shouldBe true
    }

    @Test
    fun `the temporary icon opens the window with the action the user's icon was given`() {
        var opened = 0
        val tray = tray()
        tray.installIcon(onOpen = { opened++ })
        tray.remove()
        tray.show(note("Off"))

        backend.alive.single().onAction()

        opened shouldBe 1
    }

    @Test
    fun `with the user's icon notifications and the status go through it`() {
        val tray = tray()
        tray.installIcon() shouldBe true

        tray.show(note("New chapters")) shouldBe true
        tray.status("Updating")

        backend.events shouldBe listOf("add user", "show user: New chapters", "tooltip user: Komikku – Updating")
        timer.scheduled.shouldBeEmpty()
    }

    @Test
    fun `no temporary icon where it is not used (macOS) or without a tray`() {
        tray(temporaryIcons = false).show(note("Mac")) shouldBe false

        backend.supported = false
        tray().show(note("No tray")) shouldBe false

        backend.events.shouldBeEmpty()
        timer.scheduled.shouldBeEmpty()
        exitHooks.shouldBeEmpty()
    }

    @Test
    fun `an icon that cannot be added counts as not shown and is tried again`() {
        val tray = tray()
        backend.failAdd = true

        tray.show(note("First")) shouldBe false

        timer.scheduled.shouldBeEmpty()
        tray.hasTemporaryIcon shouldBe false
        backend.failAdd = false
        tray.show(note("Second")) shouldBe true
        backend.events shouldBe listOf("add temporary", "show temporary: Second")
    }

    private class FakeTrayBackend : TrayBackend {
        var supported = true
        var failAdd = false
        val events = mutableListOf<String>()
        val alive = mutableListOf<FakeIcon>()
        var maxAlive = 0

        override fun isSupported() = supported

        override fun add(
            image: Image,
            autoSize: Boolean,
            tooltip: String,
            menu: TrayMenu?,
            onAction: () -> Unit,
        ): TrayBackend.Icon {
            if (failAdd) error("the tray refused the icon")
            val icon = FakeIcon(if (menu == null) "temporary" else "user", onAction)
            alive += icon
            maxAlive = maxOf(maxAlive, alive.size)
            events += "add ${icon.kind}"
            return icon
        }

        override fun <T> serialized(block: () -> T): T = block()

        inner class FakeIcon(val kind: String, val onAction: () -> Unit) : TrayBackend.Icon {
            override fun displayMessage(title: String, text: String, level: DesktopNotification.Level) {
                events += "show $kind: $title"
            }

            override fun setToolTip(text: String) {
                events += "tooltip $kind: $text"
            }

            override fun remove() {
                alive -= this
                events += "remove $kind"
            }
        }
    }

    private class FakeTrayTimer : TrayTimer {
        class Scheduled(val delayMillis: Long, val task: () -> Unit) {
            var cancelled = false
        }

        val scheduled = mutableListOf<Scheduled>()

        override fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit {
            val entry = Scheduled(delayMillis, task)
            scheduled += entry
            return { entry.cancelled = true }
        }

        /** The wait ends: runs the task unless it was cancelled. */
        fun elapse(entry: Scheduled = scheduled.last()) {
            if (!entry.cancelled) entry.task()
        }
    }
}
