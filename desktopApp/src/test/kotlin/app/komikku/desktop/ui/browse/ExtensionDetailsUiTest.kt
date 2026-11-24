package app.komikku.desktop.ui.browse

import android.app.Application
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.komikku.desktop.KomikkuApp
import app.komikku.desktop.di.DesktopInjekt
import app.komikku.desktop.ui.reader.ReaderLauncher
import app.komikku.shared.extension.PlatformExtensionManager
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * P22, headless: Android's "Extension info" from the sources tab — the long-press options dialog and the right-click menu
 * (which also has "Categories" now) open the extension info of the source's extension: package, version, the NSFW
 * warning, the sources' switches (one, all off, all on: Android's disabled sources), the extension's incognito switch,
 * Uninstall (the screen closes once the extension is gone). A source without an APK extension has no such entry.
 */
@OptIn(ExperimentalTestApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtensionDetailsUiTest {

    private lateinit var app: Application
    private val preferences by lazy { Injekt.get<SourcePreferences>() }
    private val first = SimpleSearchSource(id = FIRST, name = "Delta source")
    private val second = SimpleSearchSource(id = SECOND, name = "Epsilon source")
    private val extensions = FakeExtensions()

    @BeforeAll
    fun boot(@TempDir dir: File) {
        app = DesktopInjekt.init(dir)
        Injekt.addSingletonFactory<SourceManager> {
            FixedSourceManager(listOf(first, second, SimpleSearchSource(id = LONE, name = "Zeta source")))
        }
        Injekt.addSingletonFactory<PlatformExtensionManager> { extensions }
        // Independent of the machine's locale (whose language is enabled by default)
        preferences.enabledLanguages().set(setOf("all", "en"))
    }

    @BeforeEach
    fun reset() {
        Injekt.get<ReaderLauncher>().close()
        extensions.installed.value = listOf(extension())
        preferences.disabledSources().delete()
        preferences.incognitoExtensions().delete()
        preferences.sourcesTabCategories().delete()
        preferences.sourcesTabSourcesInCategories().delete()
    }

    private fun extension() = Extension.Installed(
        name = "Greek", pkgName = PKG, versionName = "1.4.7", versionCode = 7, libVersion = 1.4, lang = "en",
        isNsfw = true, signatureHash = "test", pkgFactory = null, sources = listOf(first, second), icon = null,
        isShared = false,
    )

    private fun ComposeUiTest.waitForTag(tag: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.waitForText(text: String, present: Boolean = true) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() == present }

    private fun ComposeUiTest.openBrowse() {
        setContent { KomikkuApp(context = app) }
        onNodeWithText(MR.strings.browse.localized()).performClick()
        waitForTag("source-$FIRST")
    }

    @Test
    fun `the options dialog opens the extension info, which toggles, warns and uninstalls`() =
        runDesktopComposeUiTest(width = 1200, height = 1000) {
            openBrowse()
            onNodeWithTag("source-$FIRST").performTouchInput { longClick() }
            waitForTag("source-options-extension-info")
            onNodeWithTag("source-options-extension-info").performClick()
            waitForTag("extension-details")
            waitForText(PKG)
            waitForText("1.4.7")

            // Android's age rating warning
            onNodeWithTag("extension-details-nsfw").performClick()
            waitForText(MR.strings.ext_nsfw_warning.localized())
            onNodeWithText(MR.strings.action_ok.localized()).performClick()
            waitForText(MR.strings.ext_nsfw_warning.localized(), present = false)

            // A source's switch, then all off and all on (Android's disabled sources)
            onNodeWithTag("extension-details-source-$SECOND").performClick()
            waitUntil(timeoutMillis = 5_000) { preferences.disabledSources().get() == setOf("$SECOND") }
            waitUntil(timeoutMillis = 5_000) {
                runCatching { onNodeWithTag("extension-details-source-switch-$SECOND").assertIsOff() }.isSuccess
            }
            onNodeWithTag("extension-details-source-switch-$FIRST").assertIsOn()
            onNodeWithTag("extension-details-disable-all").performClick()
            waitUntil(timeoutMillis = 5_000) { preferences.disabledSources().get() == setOf("$FIRST", "$SECOND") }
            onNodeWithTag("extension-details-enable-all").performClick()
            waitUntil(timeoutMillis = 5_000) { preferences.disabledSources().get().isEmpty() }

            // The extension's incognito mode
            onNodeWithTag("extension-details-incognito").performClick()
            waitUntil(timeoutMillis = 5_000) { preferences.incognitoExtensions().get() == setOf(PKG) }

            // Uninstall asks first (P22 review); cancelled, the extension stays
            onNodeWithTag("extension-details-uninstall").performClick()
            waitForTag("extension-uninstall-confirm")
            waitForText(KMR.strings.js_source_uninstall_confirm.localized().replace("%1\$s", "Greek"))
            onNodeWithText(MR.strings.action_cancel.localized()).performClick()
            waitForTag("extension-uninstall-confirm", present = false)
            extensions.installed.value.map { it.pkgName } shouldBe listOf(PKG)
            // Confirmed: the extension is gone, the screen closes
            onNodeWithTag("extension-details-uninstall").performClick()
            waitForTag("extension-uninstall-confirm")
            onNodeWithTag("extension-uninstall-confirm").performClick()
            waitForTag("extension-details", present = false)
            waitForTag("source-$LONE")

            // A source without an APK extension (and now the uninstalled one's) has no extension info
            onNodeWithTag("source-$LONE").performTouchInput { longClick() }
            waitForText(MR.strings.action_disable.localized())
            onAllNodesWithTag("source-options-extension-info").fetchSemanticsNodes().isEmpty() shouldBe true
        }

    @Test
    fun `the right-click menu has the categories and the extension info`() =
        runDesktopComposeUiTest(width = 1200, height = 1000) {
            preferences.sourcesTabCategories().set(setOf("Later"))
            openBrowse()
            onNodeWithTag("source-$FIRST").performMouseInput { rightClick(center) }
            waitForText(MR.strings.categories.localized())
            onNodeWithText(MR.strings.categories.localized()).performClick()
            waitForTag("source-categories-Later")
            onNodeWithTag("source-categories-Later").performClick()
            onNodeWithTag("source-categories-ok").performClick()
            waitUntil(timeoutMillis = 5_000) { preferences.sourcesTabSourcesInCategories().get() == setOf("$FIRST|Later") }

            waitForTag("source-$FIRST")
            onAllNodesWithTag("source-$FIRST")[0].performMouseInput { rightClick(center) }
            waitForText(MR.strings.label_extension_info.localized())
            onNodeWithText(MR.strings.label_extension_info.localized()).performClick()
            waitForTag("extension-details")
            waitForText(PKG)
        }

    @Test
    fun `clear cookies removes the cookies the sources get whatever their domain and says so`() =
        runDesktopComposeUiTest(width = 1200, height = 1000) {
            val jar = Injekt.get<NetworkHelper>().cookieJar
            val site = SITE.toHttpUrl()
            val other = "https://other.example.net/".toHttpUrl()
            jar.removeAll()
            // A host-only cookie and a `Domain=` one for the registrable domain (Cloudflare's); another site's cookie stays
            jar.saveFromResponse(
                site,
                listOfNotNull(
                    Cookie.parse(site, "session=1; Path=/"),
                    Cookie.parse(site, "cf_clearance=2; Domain=example.org; Path=/"),
                    // P23: a cookie with its own path, which the base address does not get
                    Cookie.parse(site, "page=4; Path=/manga"),
                ),
            )
            val mangaPage = site.resolve("/manga/1")!!
            jar.get(mangaPage).map { it.name }.toSet() shouldBe setOf("session", "cf_clearance", "page")
            jar.saveFromResponse(other, listOfNotNull(Cookie.parse(other, "keep=3; Path=/")))
            // P23 review fix: a subdomain's cookie of the source's host goes too
            val subdomain = "https://cdn.simple.example.org/".toHttpUrl()
            jar.saveFromResponse(subdomain, listOfNotNull(Cookie.parse(subdomain, "cdn=5; Path=/")))
            jar.get(site).map { it.name }.toSet() shouldBe setOf("session", "cf_clearance")

            openBrowse()
            onNodeWithTag("source-$FIRST").performTouchInput { longClick() }
            waitForTag("source-options-extension-info")
            onNodeWithTag("source-options-extension-info").performClick()
            waitForTag("extension-details-clear-cookies")
            onNodeWithTag("extension-details-clear-cookies").performClick()

            waitForText(MR.strings.cookies_cleared.localized())
            jar.get(site).shouldBeEmpty()
            jar.get(mangaPage).shouldBeEmpty()
            jar.get(subdomain).shouldBeEmpty()
            jar.get(other).map { it.name } shouldBe listOf("keep")
        }

    @Test
    fun `a failed address is reported and does not stop the others`() {
        val tried = mutableListOf<String>()
        val result = ExtensionCookies.clear(listOf("https://a.example.org", "https://b.example.org", "https://c.example.org")) { url ->
            tried += url
            if (url.startsWith("https://b.")) throw IllegalStateException("jar") else 2
        }
        tried.size shouldBe 3
        result shouldBe ExtensionCookies.Result(cleared = 4, failed = listOf("https://b.example.org"))
        ExtensionCookies.clear(listOf("https://a.example.org")) { 1 } shouldBe ExtensionCookies.Result(1, emptyList())
    }

    @Test
    fun `the extensions tab asks before it uninstalls`() =
        runDesktopComposeUiTest(width = 1200, height = 1000) {
            openBrowse()
            onNodeWithText(MR.strings.label_extensions.localized()).performClick()
            waitForTag("extension-$PKG")
            onNodeWithText(MR.strings.ext_uninstall.localized()).performClick()
            waitForTag("extension-uninstall-confirm")
            onNodeWithText(MR.strings.action_cancel.localized()).performClick()
            waitForTag("extension-uninstall-confirm", present = false)
            extensions.installed.value.map { it.pkgName } shouldBe listOf(PKG)

            onNodeWithText(MR.strings.ext_uninstall.localized()).performClick()
            waitForTag("extension-uninstall-confirm")
            onNodeWithTag("extension-uninstall-confirm").performClick()
            waitUntil(timeoutMillis = 5_000) { extensions.installed.value.isEmpty() }
        }

    private class FakeExtensions : PlatformExtensionManager {
        val installed = MutableStateFlow(emptyList<Extension.Installed>())
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
        override val installedExtensionsFlow: StateFlow<List<Extension.Installed>> = installed
        override val availableExtensionsFlow: StateFlow<List<Extension.Available>> = MutableStateFlow(emptyList())
        override val untrustedExtensionsFlow: StateFlow<List<Extension.Untrusted>> = MutableStateFlow(emptyList())
        override fun getExtensionPackage(sourceId: Long): String? =
            installed.value.find { ext -> ext.sources.any { it.id == sourceId } }?.pkgName
        override fun getSourceData(id: Long): StubSource? = null
        override suspend fun findAvailableExtensions() = Unit
        override fun installExtension(extension: Extension.Available): Flow<InstallStep> = emptyFlow()
        override fun updateExtension(extension: Extension.Installed): Flow<InstallStep> = emptyFlow()
        override fun cancelInstallUpdateExtension(extension: Extension) = Unit
        override fun uninstallExtension(extension: Extension) {
            installed.value = installed.value.filterNot { it.pkgName == extension.pkgName }
        }
        override suspend fun trust(extension: Extension.Untrusted) = Unit
    }

    private companion object {
        const val FIRST = 9_091L
        const val SECOND = 9_092L
        const val LONE = 9_093L
        const val PKG = "eu.kanade.tachiyomi.extension.en.greek"
        const val SITE = "https://simple.example.org/"
    }
}
