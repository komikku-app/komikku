package android.os

import java.io.InputStream
import java.io.OutputStream

/** android-compat: `android.os.FileUtils`. Like Android, `copy` does not close the streams. */
object FileUtils {

    @JvmStatic
    fun copy(input: InputStream, output: OutputStream): Long {
        val copied = input.copyTo(output)
        output.flush()
        return copied
    }
}
