package app.komikku.desktop.settings

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.reader.TestLibrary
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.network.browser.CloudflareHostState
import app.komikku.network.browser.CloudflareHosts
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.source.model.Page
import exh.log.EHLogLevel
import exh.source.ExhPreferences
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P12, headless: desktop Settings → Advanced (Android's layout with what the desktop has): clear cookies, the default
 * user agent's reset, the per-site user agents listed and removed, re-index downloads, the log level, "Clear database"
 * (only the selected sources' entries outside the library), and Data and storage's "Clear chapter cache" with its size.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvancedSettingsUiTest {

    private lateinit var app: Application

    @BeforeAll
    fun boot() {
        val dir = File(System.getProperty("komikku.dataDir"), "settings-advanced").apply { mkdirs() }
        app = DesktopInjekt.init(dir)
    }

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.waitForText(text: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.openSettings(entry: String) {
        Injekt.get<ReaderLauncher>().close()
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.label_more.localized()).performClick()
        waitForTag("more-settings")
        onNodeWithTag("more-settings").performClick()
        waitForTag("settings-$entry")
        onNodeWithTag("settings-$entry").performClick()
    }

    private fun ComposeUiTest.clickInAdvanced(text: String) {
        onNodeWithTag("settings-Advanced").performScrollToNode(hasText(text))
        onNodeWithText(text).performClick()
    }

    @Test
    fun `cookies, user agents, downloads index and log level`() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        val jar = Injekt.get<NetworkHelper>().cookieJar
        val url = "https://example.org/".toHttpUrl()
        jar.saveFromResponse(url, listOf(Cookie.parse(url, "session=1; Max-Age=3600")!!))
        jar.loadForRequest(url).size shouldBe 1
        val hosts = CloudflareHostState().apply {
            setUserAgent("one.example", "Agent One", clearance = null)
            setUserAgent("two.example", "Agent Two", clearance = null)
        }
        val previousHosts = CloudflareHosts.state
        CloudflareHosts.state = hosts
        try {
            runAdvancedChecks(jar, url, hosts)
        } finally {
            CloudflareHosts.state = previousHosts
        }
    }

    private fun ComposeUiTest.runAdvancedChecks(jar: eu.kanade.tachiyomi.network.AndroidCookieJar, url: okhttp3.HttpUrl, hosts: CloudflareHostState) {
        val userAgent = Injekt.get<NetworkPreferences>().defaultUserAgent()
        userAgent.set("Custom/1.0")

        openSettings("advanced")
        waitForTag("settings-Advanced")
        waitForText(KMR.strings.desktop_open_log_folder.localized())

        // Networking: clear cookies (Android's toast)
        clickInAdvanced(MR.strings.pref_clear_cookies.localized())
        waitUntil(timeoutMillis = 5_000) { jar.loadForRequest(url).isEmpty() }
        waitForText(MR.strings.cookies_cleared.localized())

        // DNS over HTTPS: Android's list (applied by the shared network client at the next start)
        val doh = Injekt.get<NetworkPreferences>().dohProvider()
        clickInAdvanced(MR.strings.pref_dns_over_https.localized())
        waitForText("Quad9")
        onNodeWithText("Quad9").performClick()
        waitUntil(timeoutMillis = 5_000) { doh.get() == PREF_DOH_QUAD9 }
        doh.set(-1)

        // The default user agent and its reset
        onNodeWithTag("settings-Advanced").performScrollToNode(hasText("Custom/1.0"))
        clickInAdvanced(MR.strings.pref_reset_user_agent_string.localized())
        waitUntil(timeoutMillis = 5_000) { userAgent.get() == userAgent.defaultValue() }

        // Per-site user agents: listed, one removed, then all
        onNodeWithTag("settings-Advanced").performScrollToNode(hasTestTag("site-user-agent-remove-one.example"))
        waitForText("Agent One")
        onNodeWithTag("site-user-agent-remove-one.example").performClick()
        waitUntil(timeoutMillis = 5_000) { hosts.userAgentOverrides().keys == setOf("two.example") }
        waitForText("Agent One", present = false)
        clickInAdvanced(KMR.strings.desktop_clear_site_user_agents.localized())
        waitUntil(timeoutMillis = 5_000) { hosts.userAgentOverrides().isEmpty() }
        onNodeWithTag("settings-Advanced").performScrollToNode(hasText(KMR.strings.desktop_site_user_agents_empty.localized()))

        // Data: re-index downloads (Android's toast)
        clickInAdvanced(MR.strings.pref_invalidate_download_cache.localized())
        waitForText(MR.strings.download_cache_invalidated.localized())

        // SY's log level
        val level = Injekt.get<ExhPreferences>().logLevel(false)
        level.set(EHLogLevel.MINIMAL.ordinal)
        clickInAdvanced(SYMR.strings.log_level.localized())
        val extreme = SYMR.strings.log_extreme.localized()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(extreme, substring = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText(extreme, substring = true)[0].performClick()
        waitUntil(timeoutMillis = 5_000) { level.get() == EHLogLevel.EXTREME.ordinal }
        level.set(EHLogLevel.MINIMAL.ordinal)
    }

    @Test
    fun `clear database removes the selected source's entries outside the library`() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        val sourceId = 4_242L
        val kept = TestLibrary.insertManga(sourceId, "/clear/library", "In the library", favorite = true)
        val removed = TestLibrary.insertManga(sourceId, "/clear/browsed", "Only browsed", favorite = false)

        openSettings("advanced")
        waitForTag("settings-Advanced")
        clickInAdvanced(MR.strings.pref_clear_database.localized())
        waitForTag("clear-database-source-$sourceId")
        onNodeWithText(MR.strings.clear_database_source_item_count.localized().replace("%1\$d", "1")).assertExists()
        onNodeWithTag("clear-database-source-$sourceId").performClick()
        onNodeWithTag("clear-database-delete").performClick()
        waitForTag("clear-database-confirm")
        onNodeWithTag("clear-database-confirm").performClick()
        waitForText(MR.strings.clear_database_completed.localized())
        waitForTag("clear-database-empty")
        runBlocking {
            Injekt.get<GetManga>().await(removed.id) shouldBe null
            Injekt.get<GetManga>().await(kept.id) shouldNotBe null
        }
    }

    @Test
    fun `data and storage clears the chapter cache and shows its size`() = runDesktopComposeUiTest(width = 1200, height = 1000) {
        val cache = Injekt.get<ChapterCache>()
        cache.putPageListToCache(Chapter.create().copy(id = 1, mangaId = 1, url = "/cache/1"), listOf(Page(0, imageUrl = "https://example.org/1.png")))
        val before = cache.readableSize

        openSettings("data")
        waitForTag("data-storage")
        onNodeWithTag("data-storage").performScrollToNode(hasTestTag("storage-usage"))
        waitForText(MR.strings.used_cache.localized().replace("%1\$s", before))
        onNodeWithText(MR.strings.pref_clear_chapter_cache.localized()).performClick()
        val cleared = MR.strings.cache_deleted.localized().substringBefore("%")
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(cleared, substring = true).fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 10_000) { cache.readableSize != before }
    }
}
