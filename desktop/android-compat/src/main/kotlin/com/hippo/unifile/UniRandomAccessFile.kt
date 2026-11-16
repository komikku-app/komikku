package com.hippo.unifile

import java.io.IOException
import java.io.RandomAccessFile

/** android-compat: UniFile's `UniRandomAccessFile`. */
interface UniRandomAccessFile {
    @Throws(IOException::class)
    fun close()

    @Throws(IOException::class)
    fun getFilePointer(): Long

    @Throws(IOException::class)
    fun seek(pos: Long)

    @Throws(IOException::class)
    fun skipBytes(n: Int): Int

    @Throws(IOException::class)
    fun length(): Long

    @Throws(IOException::class)
    fun setLength(newLength: Long)

    @Throws(IOException::class)
    fun read(b: ByteArray)

    @Throws(IOException::class)
    fun read(b: ByteArray, off: Int, len: Int)

    @Throws(IOException::class)
    fun write(b: ByteArray)

    @Throws(IOException::class)
    fun write(b: ByteArray, off: Int, len: Int)
}

internal class FileRandomAccessFile(private val file: RandomAccessFile) : UniRandomAccessFile {
    override fun close() = file.close()
    override fun getFilePointer(): Long = file.filePointer
    override fun seek(pos: Long) = file.seek(pos)
    override fun skipBytes(n: Int): Int = file.skipBytes(n)
    override fun length(): Long = file.length()
    override fun setLength(newLength: Long) = file.setLength(newLength)

    // Like UniFile on Android: reads fill the whole range or throw EOFException.
    override fun read(b: ByteArray) = file.readFully(b)
    override fun read(b: ByteArray, off: Int, len: Int) = file.readFully(b, off, len)
    override fun write(b: ByteArray) = file.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = file.write(b, off, len)
}
