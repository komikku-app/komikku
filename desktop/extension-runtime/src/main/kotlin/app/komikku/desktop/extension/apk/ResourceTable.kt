package app.komikku.desktop.extension.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal reader of an APK's compiled resource table (`resources.arsc`), enough to resolve the
 * manifest's `@string/…` label and `@mipmap/…` / `@drawable/…` icon references: simple
 * (non-bag) entries of every configuration, with the configuration's screen density.
 *
 * Written from the format description in AOSP's `ResourceTypes.h`.
 */
class ResourceTable private constructor(
    private val globalStrings: StringPool,
    private val entries: Map<Int, List<Entry>>,
) {

    /** One value of a resource in one configuration. */
    data class Entry(
        /** Screen density of the configuration (0 = any / default). */
        val density: Int,
        /** True for the default configuration (no qualifiers at all). */
        val isDefaultConfig: Boolean,
        val type: Int,
        val data: Int,
    )

    /** Every configuration's value of resource [id]. */
    fun values(id: Int): List<Entry> = entries[id].orEmpty()

    /**
     * The string value of resource [id]: the default configuration's when it has one, else the first.
     * References to other resources are followed (up to a few levels).
     */
    fun getString(id: Int): String? = resolveString(id, depth = 0)

    private fun resolveString(id: Int, depth: Int): String? {
        if (depth > MAX_REFERENCE_DEPTH) return null
        val values = values(id)
        val entry = values.firstOrNull { it.isDefaultConfig } ?: values.firstOrNull() ?: return null
        return when (entry.type) {
            ResValue.TYPE_STRING -> globalStrings.getOrNull(entry.data)
            ResValue.TYPE_REFERENCE -> resolveString(entry.data, depth + 1)
            else -> null
        }
    }

    /**
     * The file paths inside the APK of resource [id] (e.g. `res/mipmap-xxxhdpi-v4/ic_launcher.png`),
     * highest density first. References are followed.
     */
    fun getFilePaths(id: Int): List<Pair<String, Int>> = resolveFiles(id, depth = 0)
        .sortedByDescending { (_, density) -> if (density == ANY_DENSITY || density == NO_DENSITY) -1 else density }

    private fun resolveFiles(id: Int, depth: Int): List<Pair<String, Int>> {
        if (depth > MAX_REFERENCE_DEPTH) return emptyList()
        return values(id).flatMap { entry ->
            when (entry.type) {
                ResValue.TYPE_STRING -> listOfNotNull(globalStrings.getOrNull(entry.data)?.let { it to entry.density })
                ResValue.TYPE_REFERENCE -> resolveFiles(entry.data, depth + 1)
                else -> emptyList()
            }
        }
    }

    companion object {
        private const val MAX_REFERENCE_DEPTH = 5
        private const val NO_ENTRY = -1
        private const val FLAG_COMPLEX = 0x0001
        private const val FLAG_COMPACT = 0x0008
        private const val TYPE_FLAG_SPARSE = 0x01
        private const val TYPE_FLAG_OFFSET16 = 0x02
        const val ANY_DENSITY = 0xfffe
        const val NO_DENSITY = 0xffff

        fun parse(bytes: ByteArray): ResourceTable {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val type = buffer.getShort(0).toInt() and 0xffff
            if (type != ResChunk.TABLE) throw BinaryResourceException("Not a resource table (type 0x${type.toString(16)})")
            val headerSize = buffer.getShort(2).toInt() and 0xffff
            val tableSize = buffer.getInt(4).coerceAtMost(bytes.size)

            var globalStrings: StringPool? = null
            val entries = mutableMapOf<Int, MutableList<Entry>>()

            var offset = headerSize
            while (offset + 8 <= tableSize) {
                val chunkType = buffer.getShort(offset).toInt() and 0xffff
                val chunkSize = buffer.getInt(offset + 4)
                if (chunkSize < 8 || offset + chunkSize > tableSize) throw BinaryResourceException("Bad chunk at $offset")
                when (chunkType) {
                    ResChunk.STRING_POOL -> if (globalStrings == null) globalStrings = StringPool.read(buffer, offset)
                    ResChunk.TABLE_PACKAGE -> readPackage(buffer, offset, chunkSize, entries)
                }
                offset += chunkSize
            }
            return ResourceTable(
                globalStrings ?: throw BinaryResourceException("Resource table without string pool"),
                entries,
            )
        }

        private fun readPackage(buffer: ByteBuffer, start: Int, size: Int, out: MutableMap<Int, MutableList<Entry>>) {
            val headerSize = buffer.getShort(start + 2).toInt() and 0xffff
            val packageId = buffer.getInt(start + 8)
            var offset = start + headerSize
            val end = start + size
            while (offset + 8 <= end) {
                val chunkType = buffer.getShort(offset).toInt() and 0xffff
                val chunkSize = buffer.getInt(offset + 4)
                if (chunkSize < 8 || offset + chunkSize > end) throw BinaryResourceException("Bad chunk at $offset")
                if (chunkType == ResChunk.TABLE_TYPE) readType(buffer, offset, packageId, out)
                offset += chunkSize
            }
        }

        private fun readType(buffer: ByteBuffer, start: Int, packageId: Int, out: MutableMap<Int, MutableList<Entry>>) {
            val headerSize = buffer.getShort(start + 2).toInt() and 0xffff
            val typeId = buffer.get(start + 8).toInt() and 0xff
            val flags = buffer.get(start + 9).toInt() and 0xff
            val entryCount = buffer.getInt(start + 12)
            val entriesStart = buffer.getInt(start + 16)
            // ResTable_config starts at +20: size (u32), mcc, mnc (u16 each), language[2], country[2],
            // orientation, touchscreen (u8 each), density (u16).
            val config = start + 20
            val configSize = buffer.getInt(config)
            val density = if (configSize >= 16) buffer.getShort(config + 14).toInt() and 0xffff else 0
            val isDefault = (4 until configSize.coerceAtMost(headerSize - 20)).all { buffer.get(config + it).toInt() == 0 }

            val offsetsStart = start + headerSize
            val indexed: List<Pair<Int, Int>> = when {
                flags and TYPE_FLAG_SPARSE != 0 -> (0 until entryCount).map { i ->
                    val index = buffer.getShort(offsetsStart + i * 4).toInt() and 0xffff
                    val entryOffset = (buffer.getShort(offsetsStart + i * 4 + 2).toInt() and 0xffff) * 4
                    index to entryOffset
                }
                flags and TYPE_FLAG_OFFSET16 != 0 -> (0 until entryCount).map { i ->
                    val raw = buffer.getShort(offsetsStart + i * 2).toInt() and 0xffff
                    i to if (raw == 0xffff) NO_ENTRY else raw * 4
                }
                else -> (0 until entryCount).map { i -> i to buffer.getInt(offsetsStart + i * 4) }
            }

            indexed.forEach { (index, entryOffset) ->
                if (entryOffset == NO_ENTRY) return@forEach
                val at = start + entriesStart + entryOffset
                val entryFlags = buffer.getShort(at + 2).toInt() and 0xffff
                val (valueType, valueData) = when {
                    entryFlags and FLAG_COMPACT != 0 -> (entryFlags ushr 8) to buffer.getInt(at + 4)
                    entryFlags and FLAG_COMPLEX != 0 -> return@forEach // bags (styles, arrays, plurals)
                    else -> {
                        val entrySize = buffer.getShort(at).toInt() and 0xffff
                        // Res_value after the entry header: size (u16), res0 (u8), dataType (u8), data (u32)
                        (buffer.get(at + entrySize + 3).toInt() and 0xff) to buffer.getInt(at + entrySize + 4)
                    }
                }
                val id = (packageId shl 24) or (typeId shl 16) or index
                out.getOrPut(id) { mutableListOf() } += Entry(density, isDefault, valueType, valueData)
            }
        }
    }
}
