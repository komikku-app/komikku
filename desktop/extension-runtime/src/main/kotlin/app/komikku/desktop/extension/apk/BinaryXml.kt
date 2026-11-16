package app.komikku.desktop.extension.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * An element of a compiled Android XML document (the format aapt / aapt2 write for
 * `AndroidManifest.xml` and XML resources inside an APK).
 */
class XmlElement(
    val namespace: String?,
    val name: String,
    val attributes: List<XmlAttribute>,
) {
    val children: MutableList<XmlElement> = mutableListOf()

    /** Direct children named [name]. */
    fun children(name: String): List<XmlElement> = children.filter { it.name == name }

    fun child(name: String): XmlElement? = children.firstOrNull { it.name == name }

    /**
     * The attribute with framework resource id [resourceId] (`android:*` attributes are matched by id,
     * like the platform does), or, when no attribute carries that id, the attribute named [name] in
     * the `android` namespace.
     */
    fun androidAttribute(resourceId: Int, name: String): XmlAttribute? =
        attributes.firstOrNull { it.resourceId == resourceId }
            ?: attributes.firstOrNull { it.resourceId == 0 && it.name == name && it.namespace == ANDROID_NAMESPACE }

    /** The attribute named [name] without namespace (e.g. `package` on `<manifest>`). */
    fun plainAttribute(name: String): XmlAttribute? =
        attributes.firstOrNull { it.name == name && it.namespace.isNullOrEmpty() }

    companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}

/**
 * An attribute of an [XmlElement]. [type] / [data] are the typed value (`Res_value`); [rawValue] is
 * the original string when the compiler kept it.
 */
data class XmlAttribute(
    val namespace: String?,
    val name: String,
    val resourceId: Int,
    val rawValue: String?,
    val type: Int,
    val data: Int,
) {
    /** The value as a string: [rawValue], or a textual form of the typed value. */
    val stringValue: String?
        get() = when (type) {
            ResValue.TYPE_STRING -> rawValue
            ResValue.TYPE_NULL -> rawValue
            ResValue.TYPE_INT_BOOLEAN -> (data != 0).toString()
            ResValue.TYPE_FLOAT -> Float.fromBits(data).toString()
            in ResValue.TYPE_FIRST_INT..ResValue.TYPE_LAST_INT -> rawValue ?: data.toString()
            else -> rawValue
        }
}

/** `Res_value` data types (frameworks/base `ResourceTypes.h`). */
object ResValue {
    const val TYPE_NULL = 0x00
    const val TYPE_REFERENCE = 0x01
    const val TYPE_ATTRIBUTE = 0x02
    const val TYPE_STRING = 0x03
    const val TYPE_FLOAT = 0x04
    const val TYPE_DIMENSION = 0x05
    const val TYPE_FRACTION = 0x06
    const val TYPE_FIRST_INT = 0x10
    const val TYPE_INT_DEC = 0x10
    const val TYPE_INT_HEX = 0x11
    const val TYPE_INT_BOOLEAN = 0x12
    const val TYPE_LAST_INT = 0x1f
}

/** Chunk types of the Android binary resource formats (`ResourceTypes.h`). */
internal object ResChunk {
    const val STRING_POOL = 0x0001
    const val TABLE = 0x0002
    const val XML = 0x0003
    const val XML_START_NAMESPACE = 0x0100
    const val XML_END_NAMESPACE = 0x0101
    const val XML_START_ELEMENT = 0x0102
    const val XML_END_ELEMENT = 0x0103
    const val XML_CDATA = 0x0104
    const val XML_RESOURCE_MAP = 0x0180
    const val TABLE_PACKAGE = 0x0200
    const val TABLE_TYPE = 0x0201
    const val TABLE_TYPE_SPEC = 0x0202
    const val TABLE_LIBRARY = 0x0203
}

/** Thrown for malformed binary XML / resource tables. */
class BinaryResourceException(message: String) : Exception(message)

/**
 * Parser for compiled ("binary") Android XML. Written from the format description in AOSP's
 * `ResourceTypes.h`; it reads the string pool, the resource id map and the element tree, and
 * ignores namespaces' scoping, CDATA and comments (not needed for manifests).
 */
object BinaryXml {

    /** Parses [bytes] and returns the root element. */
    fun parse(bytes: ByteArray): XmlElement {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buffer.getShort(0).toInt() and 0xffff
        if (fileType != ResChunk.XML) throw BinaryResourceException("Not a binary XML file (type 0x${fileType.toString(16)})")
        val fileHeaderSize = buffer.getShort(2).toInt() and 0xffff
        val fileSize = buffer.getInt(4).coerceAtMost(bytes.size)

        var strings: StringPool? = null
        var resourceIds = IntArray(0)
        val stack = ArrayDeque<XmlElement>()
        var root: XmlElement? = null

        var offset = fileHeaderSize
        while (offset + 8 <= fileSize) {
            val type = buffer.getShort(offset).toInt() and 0xffff
            val headerSize = buffer.getShort(offset + 2).toInt() and 0xffff
            val size = buffer.getInt(offset + 4)
            if (size < 8 || offset + size > fileSize) throw BinaryResourceException("Bad chunk size $size at $offset")

            when (type) {
                ResChunk.STRING_POOL -> strings = StringPool.read(buffer, offset)
                ResChunk.XML_RESOURCE_MAP -> {
                    val count = (size - headerSize) / 4
                    resourceIds = IntArray(count) { buffer.getInt(offset + headerSize + it * 4) }
                }
                ResChunk.XML_START_ELEMENT -> {
                    val pool = strings ?: throw BinaryResourceException("Element before string pool")
                    val ext = offset + headerSize
                    val namespace = pool.getOrNull(buffer.getInt(ext))
                    val name = pool.getOrNull(buffer.getInt(ext + 4)) ?: ""
                    val attributeStart = buffer.getShort(ext + 8).toInt() and 0xffff
                    val attributeSize = buffer.getShort(ext + 10).toInt() and 0xffff
                    val attributeCount = buffer.getShort(ext + 12).toInt() and 0xffff
                    val attributes = (0 until attributeCount).map { index ->
                        val at = ext + attributeStart + index * attributeSize
                        val nameIndex = buffer.getInt(at + 4)
                        XmlAttribute(
                            namespace = pool.getOrNull(buffer.getInt(at)),
                            name = pool.getOrNull(nameIndex) ?: "",
                            resourceId = resourceIds.getOrElse(nameIndex) { 0 },
                            rawValue = pool.getOrNull(buffer.getInt(at + 8)),
                            // Res_value: size (u16), res0 (u8), dataType (u8), data (u32)
                            type = buffer.get(at + 15).toInt() and 0xff,
                            data = buffer.getInt(at + 16),
                        )
                    }
                    val element = XmlElement(namespace, name, attributes)
                    stack.lastOrNull()?.children?.add(element) ?: run { if (root == null) root = element }
                    stack.addLast(element)
                }
                ResChunk.XML_END_ELEMENT -> stack.removeLastOrNull()
                else -> Unit // namespaces, CDATA, unknown chunks
            }
            offset += size
        }
        return root ?: throw BinaryResourceException("Binary XML without elements")
    }
}

/** A `ResStringPool` (UTF-8 or UTF-16 strings; styles are ignored). */
internal class StringPool private constructor(private val strings: Array<String>) {

    val size: Int get() = strings.size

    operator fun get(index: Int): String = strings[index]

    fun getOrNull(index: Int): String? = strings.getOrNull(index)

    companion object {
        private const val UTF8_FLAG = 1 shl 8

        fun read(buffer: ByteBuffer, chunkOffset: Int): StringPool {
            val headerSize = buffer.getShort(chunkOffset + 2).toInt() and 0xffff
            val stringCount = buffer.getInt(chunkOffset + 8)
            val flags = buffer.getInt(chunkOffset + 16)
            val stringsStart = buffer.getInt(chunkOffset + 20)
            val utf8 = flags and UTF8_FLAG != 0
            if (stringCount < 0 || stringCount > 1_000_000) throw BinaryResourceException("Bad string count $stringCount")

            val strings = Array(stringCount) { index ->
                val stringOffset = buffer.getInt(chunkOffset + headerSize + index * 4)
                val at = chunkOffset + stringsStart + stringOffset
                if (utf8) readUtf8(buffer, at) else readUtf16(buffer, at)
            }
            return StringPool(strings)
        }

        private fun readUtf8(buffer: ByteBuffer, start: Int): String {
            var at = start
            // UTF-16 length (1 or 2 bytes), then UTF-8 byte length (1 or 2 bytes)
            at += if (buffer.get(at).toInt() and 0x80 != 0) 2 else 1
            val first = buffer.get(at).toInt() and 0xff
            val length = if (first and 0x80 != 0) {
                ((first and 0x7f) shl 8) or (buffer.get(at + 1).toInt() and 0xff)
            } else {
                first
            }
            at += if (first and 0x80 != 0) 2 else 1
            val bytes = ByteArray(length) { buffer.get(at + it) }
            return String(bytes, Charsets.UTF_8)
        }

        private fun readUtf16(buffer: ByteBuffer, start: Int): String {
            var at = start
            val first = buffer.getShort(at).toInt() and 0xffff
            val length = if (first and 0x8000 != 0) {
                ((first and 0x7fff) shl 16) or (buffer.getShort(at + 2).toInt() and 0xffff)
            } else {
                first
            }
            at += if (first and 0x8000 != 0) 4 else 2
            val chars = CharArray(length) { buffer.getChar(at + it * 2) }
            return String(chars)
        }
    }
}
