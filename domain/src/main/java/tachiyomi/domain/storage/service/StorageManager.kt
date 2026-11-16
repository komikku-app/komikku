package tachiyomi.domain.storage.service

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import tachiyomi.core.common.preference.Preference

class StorageManager(
    private val context: Context,
    storagePreferences: StoragePreferences,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var baseDir: UniFile? = getBaseDir(storagePreferences.baseStorageDirectory().get())

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        storagePreferences.baseStorageDirectory().changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach { uri ->
                baseDir = getBaseDir(uri)
                baseDir?.let { parent ->
                    parent.createDirectory(AUTOMATIC_BACKUPS_PATH)
                    parent.createDirectory(LOCAL_SOURCE_PATH)
                    parent.createDirectory(DOWNLOADS_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                }
                _changes.send(Unit)
            }
            .launchIn(scope)
    }

    private fun getBaseDir(uri: String): UniFile? {
        return UniFile.fromUri(context, uri.toUri())
            .takeIf {
                // KMK -->
                it?.isAccessibleDirectory == true
                // KMK <--
            }
    }

    fun getAutomaticBackupsDirectory(): UniFile? {
        return baseDir?.createDirectory(AUTOMATIC_BACKUPS_PATH)
    }

    fun getDownloadsDirectory(): UniFile? {
        return baseDir?.createDirectory(DOWNLOADS_PATH)
    }

    fun getLocalSourceDirectory(): UniFile? {
        return baseDir?.createDirectory(LOCAL_SOURCE_PATH)
    }

    // SY -->
    fun getLogsDirectory(): UniFile? {
        return baseDir?.createDirectory(LOGS_PATH)
    }
    // SY <--

    companion object {
        // KMK -->
        /**
         * Extension property to check if a UniFile is an accessible directory
         */
        val UniFile.isAccessibleDirectory: Boolean
            get() = exists() && isDirectory && canWrite() && canRead()

        /**
         * Check if a directory is accessible
         */
        fun directoryAccessible(context: Context, uri: String): Boolean {
            return UniFile.fromUri(context, uri.toUri())?.isAccessibleDirectory == true
        }

        /**
         * Call FilePicker to allow access to storage or request All Files Access Permission if not available.
         *
         * Platform part in `allowAccessStoragePlatform` (Android: document tree picker or the All Files
         * Access permission flow, `src/androidMain/.../StorageAccess.android.kt`; desktop: folder picker).
         */
        fun allowAccessStorage(
            context: Context,
            storageDirPref: Preference<String>,
            pickStorageLocation: () -> Unit,
        ) {
            allowAccessStoragePlatform(context, storageDirPref, pickStorageLocation)
        }
        // KMK <--
    }
}

private const val AUTOMATIC_BACKUPS_PATH = "autobackup"
private const val DOWNLOADS_PATH = "downloads"
private const val LOCAL_SOURCE_PATH = "local"

// SY -->
private const val LOGS_PATH = "logs"
// SY <--
