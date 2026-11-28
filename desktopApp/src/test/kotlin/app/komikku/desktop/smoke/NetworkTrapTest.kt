package app.komikku.desktop.smoke

import app.komikku.desktop.di.DesktopInjekt
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * The smoke mode's network trap, installed before the DI start (ANSWER 011, condition 5): the app's own client, a
 * fresh OkHttp client, `URLConnection` and a plain socket all fail, the server sees nothing, and every attempt is listed
 * with its call site.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetworkTrapTest {

    private val server = MockWebServer()
    private val trap = NetworkTrap()

    @BeforeAll
    fun boot(@TempDir dataDir: File) {
        server.start()
        trap.install()
        DesktopInjekt.init(dataDir)
    }

    @AfterAll
    fun stop() {
        trap.uninstall()
        server.close()
    }

    @Test
    fun `every kind of connection fails, reaches nothing and is listed with its call site`() {
        val appClient = Injekt.get<NetworkHelper>().client

        shouldThrow<IOException> { appClient.newCall(Request.Builder().url(server.url("/app-client")).build()).execute() }
        shouldThrow<IOException> { OkHttpClient().newCall(Request.Builder().url(server.url("/fresh-client")).build()).execute() }
        shouldThrow<IOException> {
            (URI(server.url("/url-connection").toString()).toURL().openConnection() as HttpURLConnection).inputStream
        }
        shouldThrow<IOException> { Socket().use { it.connect(InetSocketAddress(server.hostName, server.port), 2_000) } }

        server.requestCount shouldBe 0
        // OkHttp asks for the address only (`http://host:port/`), `URLConnection` for the whole URL
        val attempts = trap.attempts.filter { attempt -> attempt.callSite.any { it.contains(testClass) } }
        attempts.map { it.uri }.forEach { it shouldContain ":${server.port}" }
        attempts.map { it.uri }.toSet() shouldBe setOf(
            "http://${server.hostName}:${server.port}/",
            "http://${server.hostName}:${server.port}/url-connection",
            "socket://${server.hostName}:${server.port}",
        )
        attempts.map { attempt -> attempt.callSite.first { it.contains(testClass) } }.distinct() shouldHaveSize 4
    }

    private val testClass = NetworkTrapTest::class.java.name
}
