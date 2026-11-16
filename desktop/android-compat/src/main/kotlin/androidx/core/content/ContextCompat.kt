package androidx.core.content

import android.content.Context
import java.io.File
import java.util.concurrent.Executor

/** android-compat: the AndroidX `ContextCompat` helpers used by shared code. */
object ContextCompat {

    @JvmStatic
    fun getExternalFilesDirs(context: Context, type: String?): Array<File?> = context.getExternalFilesDirs(type)

    @JvmStatic
    fun getExternalCacheDirs(context: Context): Array<File?> = context.externalCacheDirs

    @JvmStatic
    fun getDataDir(context: Context): File = context.dataDir

    @JvmStatic
    fun getMainExecutor(context: Context): Executor = context.mainExecutor
}
