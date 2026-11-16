package app.komikku.desktop.extension.testing

import app.komikku.desktop.extension.testing.AxmlWriter.Companion.chunk
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.int32
import app.komikku.desktop.extension.testing.AxmlWriter.Companion.stringPool
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Test-side writer of a minimal compiled resource table (`resources.arsc`) with package id 0x7f:
 * string-valued entries (strings and file paths) per type, each in one or more density
 * configurations. Resource ids are `0x7f << 24 | typeId << 16 | entryIndex` with type ids from 1 in
 * the order types are added.
 */
class ArscWriter {

    private class Value(val density: Int, val string: String)

    private val types = linkedMapOf<String, MutableList<Pair<String, MutableList<Value>>>>()

    /** Adds entry [key] of [type] with [string] in the configuration with [density] (0 = default). Returns its id. */
    fun add(type: String, key: String, string: String, density: Int = 0): Int {
        val entries = types.getOrPut(type) { mutableListOf() }
        val index = entries.indexOfFirst { it.first == key }.takeIf { it >= 0 }
            ?: run {
                entries += key to mutableListOf()
                entries.size - 1
            }
        entries[index].second += Value(density, string)
        val typeId = types.keys.indexOf(type) + 1
        return (0x7f shl 24) or (typeId shl 16) or index
    }

    fun write(): ByteArray {
        val globalStrings = types.values.flatMap { entries -> entries.flatMap { (_, values) -> values.map { it.string } } }.distinct()
        val keyStrings = types.values.flatMap { entries -> entries.map { it.first } }.distinct()
        val typeStrings = types.keys.toList()

        val packageBody = ByteArrayOutputStream()
        val typeStringsChunk = stringPool(typeStrings, utf8 = false)
        val keyStringsChunk = stringPool(keyStrings, utf8 = true)
        val packageHeaderSize = 288
        types.entries.forEachIndexed { typeIndex, (_, entries) ->
            val densities = entries.flatMap { (_, values) -> values.map { it.density } }.distinct()
            densities.forEach { density ->
                packageBody.write(typeChunk(typeIndex + 1, density, entries, globalStrings, keyStrings))
            }
        }

        val header = ByteBuffer.allocate(packageHeaderSize - 8).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x7f)
        "fixture".padEnd(128, '\u0000').forEach { header.putChar(it) }
        header.putInt(packageHeaderSize) // typeStrings
        header.putInt(typeStrings.size) // lastPublicType
        header.putInt(packageHeaderSize + typeStringsChunk.size) // keyStrings
        header.putInt(keyStrings.size) // lastPublicKey
        header.putInt(0) // typeIdOffset
        val packageChunk = chunk(
            0x0200,
            packageHeaderSize,
            header.array() + typeStringsChunk + keyStringsChunk + packageBody.toByteArray(),
        )

        val table = stringPool(globalStrings, utf8 = true) + packageChunk
        return chunk(0x0002, 12, int32(1) + table)
    }

    private fun typeChunk(
        typeId: Int,
        density: Int,
        entries: List<Pair<String, List<Value>>>,
        globalStrings: List<String>,
        keyStrings: List<String>,
    ): ByteArray {
        val configSize = 64
        val headerSize = 20 + configSize
        val offsets = ByteArrayOutputStream()
        val data = ByteArrayOutputStream()
        entries.forEach { (key, values) ->
            val value = values.firstOrNull { it.density == density }
            if (value == null) {
                offsets.write(int32(-1))
                return@forEach
            }
            offsets.write(int32(data.size()))
            val entry = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            entry.putShort(8) // entry size
            entry.putShort(0) // flags
            entry.putInt(keyStrings.indexOf(key))
            entry.putShort(8) // Res_value size
            entry.put(0)
            entry.put(AxmlWriter.TYPE_STRING.toByte())
            entry.putInt(globalStrings.indexOf(value.string))
            data.write(entry.array())
        }
        val header = ByteBuffer.allocate(headerSize - 8).order(ByteOrder.LITTLE_ENDIAN)
        header.put(typeId.toByte())
        header.put(0) // flags
        header.putShort(0)
        header.putInt(entries.size)
        header.putInt(headerSize + entries.size * 4) // entriesStart
        // ResTable_config: size, then zeroes except the density (offset 14)
        val config = ByteBuffer.allocate(configSize).order(ByteOrder.LITTLE_ENDIAN)
        config.putInt(configSize)
        config.putShort(14, density.toShort())
        header.put(config.array())
        return chunk(0x0201, headerSize, header.array() + offsets.toByteArray() + data.toByteArray())
    }
}
