package exh.md.handlers

import exh.md.dto.MangaPlusDecodedViewer
import exh.md.dto.MangaPlusViewer
import exh.md.dto.MangaPlusViewerError
import exh.md.dto.MangaPlusViewerImage
import exh.md.dto.MangaPlusViewerPage
import exh.md.dto.MangaPlusViewerPages
import exh.md.dto.MangaPlusViewerPopup
import exh.md.dto.MangaPlusViewerResponse
import exh.md.dto.MangaPlusViewerSuccess
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MangaPlusHandlerTest {

    @Test
    fun `viewer request uses the protobuf endpoint and keeps a session token`() {
        val handler = MangaPlusHandler(OkHttpClient())
        val request = handler.pageListRequest("1008191", dataSaver = false)

        assertEquals("manga_viewer_v3", request.url.pathSegments.last())
        assertEquals("1008191", request.url.queryParameter("chapter_id"))
        assertNull(request.url.queryParameter("format"))
        assertEquals("yes", request.url.queryParameter("split"))
        assertEquals("super_high", request.url.queryParameter("img_quality"))
        assertEquals("eng", request.url.queryParameter("clang"))
        assertTrue(request.header("SESSION-TOKEN")!!.isNotBlank())
        assertTrue(request.header("Referer")!!.endsWith("/viewer/1008191"))

        val dataSaver = handler.pageListRequest("1008191", dataSaver = true)
        assertEquals("low", dataSaver.url.queryParameter("img_quality"))
    }

    @Test
    fun `protobuf viewer fixture decodes page url and encryption key`() {
        val payload = MangaPlusViewerResponse(
            success = MangaPlusViewerSuccess(
                mangaViewer = MangaPlusViewer(
                    pages = listOf(
                        MangaPlusViewerPage(
                            mangaPage = MangaPlusViewerImage(
                                imageUrl = "https://example.test/page.jpg",
                                encryptionKey = "abcd",
                            ),
                        ),
                    ),
                    viewToken = "view-token",
                ),
            ),
        )
        val bytes = ProtoBuf.encodeToByteArray(MangaPlusViewerResponse.serializer(), payload)
        assertEquals(setOf(1), fieldNumbers(bytes))
        val success = fieldNumbers(payloadOf(bytes, 1))
        assertEquals(setOf(10), success)
        val viewer = fieldNumbers(payloadOf(payloadOf(bytes, 1), 10))
        assertTrue(1 in viewer)
        assertTrue(19 in viewer)

        val decoded = MangaPlusViewerPages.decode(ProtoBuf, bytes)
        assertEquals("https://example.test/page.jpg", decoded.pages.single().imageUrl)
        assertEquals("abcd", decoded.pages.single().encryptionKey)
        assertEquals("view-token", decoded.viewToken)
        assertNull(decoded.errorMessage)
    }

    @Test
    fun `protobuf error popup becomes an exception message`() {
        val payload = MangaPlusViewerResponse(
            error = MangaPlusViewerError(
                englishPopup = MangaPlusViewerPopup(
                    subject = "Not Found",
                    body = "This chapter is no longer available",
                ),
            ),
        )
        val bytes = ProtoBuf.encodeToByteArray(MangaPlusViewerResponse.serializer(), payload)
        assertEquals(setOf(2), fieldNumbers(bytes))
        val popupFields = fieldNumbers(payloadOf(payloadOf(bytes, 2), 2))
        assertEquals(setOf(1, 2), popupFields)

        val decoded: MangaPlusDecodedViewer = MangaPlusViewerPages.decode(ProtoBuf, bytes)
        assertEquals("This chapter is no longer available", decoded.errorMessage)
        assertTrue(decoded.pages.isEmpty())
    }

    private fun fieldNumbers(bytes: ByteArray): Set<Int> {
        return lengthDelimitedFields(bytes).map { it.first }.toSet()
    }

    private fun payloadOf(bytes: ByteArray, field: Int): ByteArray {
        return lengthDelimitedFields(bytes).first { it.first == field }.second
    }

    private fun lengthDelimitedFields(bytes: ByteArray): List<Pair<Int, ByteArray>> {
        val fields = mutableListOf<Pair<Int, ByteArray>>()
        var index = 0
        while (index < bytes.size) {
            val tag = readVarint(bytes, index)
            index = tag.second
            val fieldNumber = tag.first ushr 3
            when (val wire = tag.first and 0x7) {
                0 -> index = readVarint(bytes, index).second
                1 -> index += 8
                5 -> index += 4
                2 -> {
                    val length = readVarint(bytes, index)
                    val start = length.second
                    val end = start + length.first
                    fields.add(fieldNumber to bytes.copyOfRange(start, end))
                    index = end
                }
                else -> error("Unsupported protobuf wire type $wire")
            }
        }
        return fields
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Int, Int> {
        var value = 0
        var shift = 0
        var index = start
        while (index < bytes.size) {
            val byte = bytes[index].toInt() and 0xFF
            index += 1
            value = value or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) return value to index
            shift += 7
        }
        error("Truncated protobuf varint")
    }
}
