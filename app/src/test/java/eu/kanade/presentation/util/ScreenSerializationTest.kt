package eu.kanade.presentation.util

import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsMainScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Navigator stacks are saved with Java serialization, so a restored screen must still be the same screen.
 */
class ScreenSerializationTest {

    @Test
    fun `class screen restores as a copy with the same key`() {
        val screen = AboutScreen()

        val restored = screen.roundTrip().shouldBeInstanceOf<AboutScreen>()

        restored.key shouldBe screen.key
    }

    @Test
    fun `object screens restore as the same instance`() {
        listOf(HomeScreen, DownloadQueueScreen, SettingsMainScreen, SettingsDataScreen).forEach {
            it.roundTrip() shouldBeSameInstanceAs it
        }
    }

    private fun Screen.roundTrip(): Any {
        val bytes = ByteArrayOutputStream().use { bytes ->
            ObjectOutputStream(bytes).use { it.writeObject(this) }
            bytes.toByteArray()
        }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
    }
}
