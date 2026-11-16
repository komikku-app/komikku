package tachiyomi.core.common.i18n

import android.app.Application
import dev.icerock.moko.resources.desc.StringDesc
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.i18n.MR
import java.io.File

class LocalizeTest {

    @TempDir
    lateinit var dir: File

    private val context by lazy { Application(dir) }

    private lateinit var previousLocale: StringDesc.LocaleType

    @BeforeEach
    fun setLocale() {
        previousLocale = StringDesc.localeType
        StringDesc.localeType = StringDesc.LocaleType.Custom("en")
    }

    @AfterEach
    fun restoreLocale() {
        StringDesc.localeType = previousLocale
    }

    @Test
    fun `context helpers resolve strings on desktop`() {
        context.stringResource(MR.strings.app_name) shouldBe "Komikku"
        context.stringResource(MR.strings.pref_relative_format_summary, "a", "b") shouldBe "\"a\" instead of \"b\""
        context.pluralStringResource(MR.plurals.relative_time, 1) shouldBe "Yesterday"
        context.pluralStringResource(MR.plurals.relative_time, 3, 3) shouldBe "3 days ago"
    }

    @Test
    fun `context-free helpers give the same results`() {
        stringResource(MR.strings.app_name) shouldBe context.stringResource(MR.strings.app_name)
        stringResource(MR.strings.pref_relative_format_summary, "a", "b") shouldBe "\"a\" instead of \"b\""
        pluralStringResource(MR.plurals.relative_time, 1) shouldBe "Yesterday"
        pluralStringResource(MR.plurals.relative_time, 3, 3) shouldBe "3 days ago"
    }

    @Test
    fun `follows the moko locale`() {
        StringDesc.localeType = StringDesc.LocaleType.Custom("fr")
        stringResource(MR.strings.label_library) shouldBe "Bibliothèque"
        context.stringResource(MR.strings.label_library) shouldBe "Bibliothèque"
    }
}
