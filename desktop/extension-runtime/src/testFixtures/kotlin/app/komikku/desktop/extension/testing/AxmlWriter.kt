package app.komikku.desktop.extension.testing

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Test-side writer of compiled ("binary") Android XML, the format `aapt2` produces for
 * `AndroidManifest.xml`. Supports what extension manifests use: one namespace, elements and typed
 * attributes, attribute resource ids (resource map), UTF-8 or UTF-16 string pools.
 */
class AxmlWriter(private val utf8: Boolean = true) {

    class Attr(
        val namespace: String?,
        val name: String,
        val resourceId: Int,
        val rawValue: String?,
        val type: Int,
        val data: Int,
    )

    class Node(val name: String, val attributes: List<Attr> = emptyList(), val children: List<Node> = emptyList())

    fun write(root: Node): ByteArray {
        // Attribute names with a resource id come first, so the resource map indexes them.
        val withIds = linkedMapOf<String, Int>()
        fun collectIds(node: Node) {
            node.attributes.filter { it.resourceId != 0 }.forEach { withIds.putIfAbsent(it.name, it.resourceId) }
            node.children.forEach(::collectIds)
        }
        collectIds(root)
        val strings = mutableListOf<String>()
        strings += withIds.keys
        fun intern(value: String?): Int {
            if (value == null) return -1
            val index = strings.indexOf(value)
            if (index >= 0) return index
            strings += value
            return strings.size - 1
        }
        intern(ANDROID_PREFIX)
        intern(ANDROID_NS)
        fun collectStrings(node: Node) {
            intern(node.name)
            node.attributes.forEach {
                intern(it.name)
                intern(it.namespace)
                intern(it.rawValue)
            }
            node.children.forEach(::collectStrings)
        }
        collectStrings(root)

        val body = ByteArrayOutputStream()
        body.write(stringPool(strings, utf8))
        body.write(chunk(RESOURCE_MAP, 8, withIds.values.flatMap { int32(it).toList() }.toByteArray()))
        body.write(namespaceChunk(START_NAMESPACE, strings.indexOf(ANDROID_PREFIX), strings.indexOf(ANDROID_NS)))
        fun writeNode(node: Node) {
            val ext = ByteBuffer.allocate(20 + 20 * node.attributes.size).order(ByteOrder.LITTLE_ENDIAN)
            ext.putInt(-1) // namespace
            ext.putInt(strings.indexOf(node.name))
            ext.putShort(20) // attributeStart
            ext.putShort(20) // attributeSize
            ext.putShort(node.attributes.size.toShort())
            ext.putShort(0) // idIndex
            ext.putShort(0) // classIndex
            ext.putShort(0) // styleIndex
            node.attributes.forEach { attr ->
                ext.putInt(intern(attr.namespace))
                ext.putInt(strings.indexOf(attr.name))
                ext.putInt(intern(attr.rawValue))
                ext.putShort(8)
                ext.put(0)
                ext.put(attr.type.toByte())
                ext.putInt(attr.data)
            }
            body.write(nodeChunk(START_ELEMENT, ext.array()))
            node.children.forEach(::writeNode)
            body.write(nodeChunk(END_ELEMENT, int32(-1) + int32(strings.indexOf(node.name))))
        }
        writeNode(root)
        body.write(namespaceChunk(END_NAMESPACE, strings.indexOf(ANDROID_PREFIX), strings.indexOf(ANDROID_NS)))
        return chunk(XML, 8, body.toByteArray())
    }

    private fun namespaceChunk(type: Int, prefix: Int, uri: Int) = nodeChunk(type, int32(prefix) + int32(uri))

    /** ResXMLTree_node: header + line number + comment index. */
    private fun nodeChunk(type: Int, ext: ByteArray) = chunk(type, 16, int32(1) + int32(-1) + ext)

    companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val ANDROID_PREFIX = "android"

        private const val STRING_POOL = 0x0001
        private const val XML = 0x0003
        private const val START_NAMESPACE = 0x0100
        private const val END_NAMESPACE = 0x0101
        private const val START_ELEMENT = 0x0102
        private const val END_ELEMENT = 0x0103
        private const val RESOURCE_MAP = 0x0180

        const val TYPE_REFERENCE = 0x01
        const val TYPE_STRING = 0x03
        const val TYPE_FLOAT = 0x04
        const val TYPE_INT_DEC = 0x10
        const val TYPE_INT_BOOLEAN = 0x12

        // android.R.attr ids
        const val ATTR_LABEL = 0x01010001
        const val ATTR_ICON = 0x01010002
        const val ATTR_NAME = 0x01010003
        const val ATTR_VALUE = 0x01010024
        const val ATTR_VERSION_CODE = 0x0101021b
        const val ATTR_VERSION_NAME = 0x0101021c
        const val ATTR_VERSION_CODE_MAJOR = 0x01010576

        fun androidString(name: String, id: Int, value: String) = Attr(ANDROID_NS, name, id, value, TYPE_STRING, -1)
        fun androidInt(name: String, id: Int, value: Int) = Attr(ANDROID_NS, name, id, null, TYPE_INT_DEC, value)
        fun androidFloat(name: String, id: Int, value: Float) = Attr(ANDROID_NS, name, id, null, TYPE_FLOAT, value.toRawBits())
        fun androidRef(name: String, id: Int, resId: Int) = Attr(ANDROID_NS, name, id, null, TYPE_REFERENCE, resId)
        fun plainString(name: String, value: String) = Attr(null, name, 0, value, TYPE_STRING, -1)

        internal fun chunk(type: Int, headerSize: Int, rest: ByteArray): ByteArray {
            val buffer = ByteBuffer.allocate(8 + rest.size).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putShort(type.toShort())
            buffer.putShort(headerSize.toShort())
            buffer.putInt(8 + rest.size)
            buffer.put(rest)
            return buffer.array()
        }

        internal fun int32(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

        /** A `ResStringPool` chunk without styles. */
        internal fun stringPool(strings: List<String>, utf8: Boolean): ByteArray {
            val data = ByteArrayOutputStream()
            val offsets = strings.map { value ->
                val offset = data.size()
                if (utf8) {
                    val bytes = value.toByteArray(Charsets.UTF_8)
                    data.write(utf8Length(value.length))
                    data.write(utf8Length(bytes.size))
                    data.write(bytes)
                    data.write(0)
                } else {
                    require(value.length < 0x8000)
                    data.write(int16(value.length))
                    value.forEach { data.write(int16(it.code)) }
                    data.write(int16(0))
                }
                offset
            }
            while (data.size() % 4 != 0) data.write(0)
            val headerSize = 28
            val header = ByteBuffer.allocate(20 + 4 * strings.size).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(strings.size)
            header.putInt(0) // styles
            header.putInt(if (utf8) 1 shl 8 else 0)
            header.putInt(headerSize + 4 * strings.size) // stringsStart
            header.putInt(0) // stylesStart
            offsets.forEach { header.putInt(it) }
            return chunk(STRING_POOL, headerSize, header.array() + data.toByteArray())
        }

        private fun int16(value: Int): ByteArray = byteArrayOf((value and 0xff).toByte(), (value shr 8 and 0xff).toByte())

        private fun utf8Length(length: Int): ByteArray = if (length < 0x80) {
            byteArrayOf(length.toByte())
        } else {
            require(length < 0x8000)
            byteArrayOf(((length shr 8) or 0x80).toByte(), (length and 0xff).toByte())
        }
    }
}
