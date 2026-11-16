package android.os

import java.io.File

/**
 * android-compat: `android.os.StatFs` over `java.io.File` space queries. Sizes are reported with a
 * block size of 1 byte, so `blockCount * blockSize` etc. give the same totals as on Android.
 */
class StatFs(path: String) {

    private var file = resolve(path)

    fun restat(path: String) {
        file = resolve(path)
    }

    val blockSizeLong: Long get() = 1L
    val blockCountLong: Long get() = file.totalSpace
    val availableBlocksLong: Long get() = file.usableSpace
    val freeBlocksLong: Long get() = file.freeSpace
    val totalBytes: Long get() = file.totalSpace
    val availableBytes: Long get() = file.usableSpace
    val freeBytes: Long get() = file.freeSpace

    private fun resolve(path: String): File {
        val file = File(path)
        require(file.exists()) { "Invalid path: $path" }
        return file
    }
}
