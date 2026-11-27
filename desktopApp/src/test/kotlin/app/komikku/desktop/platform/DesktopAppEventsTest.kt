package app.komikku.desktop.platform

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import java.awt.desktop.QuitResponse

/**
 * [DesktopAppEvents]: a quit request from the OS closes the app first and is answered only by [DesktopAppEvents.finishQuit]
 * (once), a second request replaces the first; hiding reports like minimising.
 */
class DesktopAppEventsTest {

    private class Response(val events: MutableList<String>, val name: String) : QuitResponse {
        override fun performQuit() {
            events += "$name: perform"
        }

        override fun cancelQuit() {
            events += "$name: cancel"
        }
    }

    @Test
    fun `a quit request closes the app, then goes through once the work stopped`() {
        val events = mutableListOf<String>()

        DesktopAppEvents.onQuitRequest(Response(events, "first")) { events += "close app" }
        events shouldContainExactly listOf("close app")

        DesktopAppEvents.finishQuit()
        DesktopAppEvents.finishQuit()
        events shouldContainExactly listOf("close app", "first: perform")
    }

    @Test
    fun `a second quit request replaces the pending one`() {
        val events = mutableListOf<String>()

        DesktopAppEvents.onQuitRequest(Response(events, "first")) {}
        DesktopAppEvents.onQuitRequest(Response(events, "second")) {}
        DesktopAppEvents.finishQuit()

        events shouldContainExactly listOf("first: cancel", "second: perform")
    }

    @Test
    fun `hiding and showing the app are reported`() {
        val events = mutableListOf<String>()
        val listener = DesktopAppEvents.hideListener(onHidden = { events += "hidden" }, onShown = { events += "shown" })

        listener.appHidden(null)
        listener.appUnhidden(null)

        events shouldContainExactly listOf("hidden", "shown")
    }
}
