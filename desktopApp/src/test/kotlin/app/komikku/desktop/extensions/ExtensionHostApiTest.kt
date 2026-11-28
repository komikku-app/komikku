package app.komikku.desktop.extensions

import app.komikku.desktop.di.DesktopInjekt
import app.komikku.shared.reader.image.SkiaPageDecoder
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.BuildConfig
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * What extensions take from the host app besides the source API: `eu.kanade.tachiyomi.AppInfo` (MangaDex's headers
 * call it) and the `http.agent` system property (MangaDex's user agent), both always there on Android.
 */
class ExtensionHostApiTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `AppInfo answers like on Android`() {
        AppInfo.getVersionName() shouldBe BuildConfig.VERSION_NAME
        AppInfo.getVersionCode() shouldBe BuildConfig.VERSION_CODE
        AppInfo.getSupportedImageMimeTypes() shouldContain "image/jpeg"
    }

    @Test
    fun `AppInfo lists exactly the image types the desktop reader decodes`() {
        // Extensions build their Accept header from it: AVIF / JPEG XL / HEIF pages would show as errors (review M3)
        AppInfo.getSupportedImageMimeTypes() shouldContainExactly listOf("image/gif", "image/jpeg", "image/png", "image/webp")
        AppInfo.getSupportedImageMimeTypes() shouldContainExactly SkiaPageDecoder.DECODED.map { it.mime }
    }

    @Test
    fun `init sets http agent unless it is set`() {
        val before = System.getProperty("http.agent")
        try {
            System.clearProperty("http.agent")
            DesktopInjekt.init(dir.resolve("data"))
            System.getProperty("http.agent") shouldStartWith "Komikku/${BuildConfig.VERSION_NAME} ("

            System.setProperty("http.agent", "custom")
            DesktopInjekt.init(dir.resolve("data"))
            System.getProperty("http.agent") shouldBe "custom"
        } finally {
            if (before == null) System.clearProperty("http.agent") else System.setProperty("http.agent", before)
        }
    }
}
