package extprobe

import android.content.Context
import android.os.Environment
import java.io.File

/** Stands for extension code: [ExtensionDataFoldersTest] defines it again in a loader that owns a data folder. */
class PathProbe {

    fun paths(context: Context): Map<String, File?> = mapOf(
        "dataDir" to context.dataDir,
        "filesDir" to context.filesDir,
        "cacheDir" to context.cacheDir,
        "externalCacheDir" to context.externalCacheDir,
        "externalFilesDir" to context.getExternalFilesDir("covers"),
        "dir" to context.getDir("webview", Context.MODE_PRIVATE),
        "databasePath" to context.getDatabasePath("x.db"),
        "applicationInfo.dataDir" to context.applicationInfo.dataDir?.let(::File),
        "applicationContext.cacheDir" to context.applicationContext.cacheDir,
        "Environment.dataDirectory" to Environment.getDataDirectory(),
        "Environment.downloadCacheDirectory" to Environment.getDownloadCacheDirectory(),
    )
}
