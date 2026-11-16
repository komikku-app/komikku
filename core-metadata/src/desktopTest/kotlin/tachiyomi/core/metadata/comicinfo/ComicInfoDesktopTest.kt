package tachiyomi.core.metadata.comicinfo

import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import org.junit.jupiter.api.Test

/** ComicInfo.xml round trip with the app's XML configuration on the JVM xmlutil artifact. */
class ComicInfoDesktopTest {

    private val xml = XML {
        defaultPolicy {
            ignoreUnknownChildren()
        }
        autoPolymorphic = true
        xmlDeclMode = XmlDeclMode.Charset
        indent = 2
        xmlVersion = XmlVersion.XML10
    }

    @Test
    fun `encodes and decodes manga details`() {
        val manga = SManga.create().apply {
            title = "Title"
            author = "Writer"
            artist = "Artist"
            description = "Summary & more"
            genre = "Action, Comedy"
            status = SManga.ONGOING
        }

        val encoded = xml.encodeToString(ComicInfo.serializer(), manga.getComicInfo())
        encoded shouldContain "<Series>Title</Series>"
        encoded shouldContain "Summary &amp; more"

        val decoded = SManga.create()
        decoded.copyFromComicInfo(xml.decodeFromString(ComicInfo.serializer(), encoded))
        decoded.title shouldBe "Title"
        decoded.author shouldBe "Writer"
        decoded.artist shouldBe "Artist"
        decoded.description shouldBe "Summary & more"
        decoded.genre shouldBe "Action, Comedy"
        decoded.status shouldBe SManga.ONGOING
    }

    @Test
    fun `ignores unknown elements`() {
        val input = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <Series>Series</Series>
              <Unknown>value</Unknown>
              <Number>3</Number>
            </ComicInfo>
        """.trimIndent()
        val info = xml.decodeFromString(ComicInfo.serializer(), input)
        info.series?.value shouldBe "Series"
        info.number?.value shouldBe "3"
    }
}
