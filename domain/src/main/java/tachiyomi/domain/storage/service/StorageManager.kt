package tachiyomi.domain.storage.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.toast
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
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import java.io.File

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
         */
        fun allowAccessStorage(
            context: Context,
            storageDirPref: Preference<String>,
            pickStorageLocation: () -> Unit,
        ) {
            try {
                val documentTreeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                if (isIntentAvailable(context, documentTreeIntent)) {
                    pickStorageLocation()
                } else {
                    handleStoragePermission(context, storageDirPref)
                }
            } catch (e: ActivityNotFoundException) {
                fallbackToScopedStorage(context, storageDirPref)
            }
        }

        /**
         * Handle storage permissions for Android R and above
         */
        private fun handleStoragePermission(
            context: Context,
            storageDirPref: Preference<String>,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    try {
                        val permissionIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        context.startActivity(permissionIntent)
                    } catch (e: ActivityNotFoundException) {
                        val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        context.startActivity(fallbackIntent)
                    }
                } else {
                    updateStoragePreference(context, storageDirPref)
                }
            } else {
                throw ActivityNotFoundException(context.stringResource(MR.strings.file_picker_error))
            }
        }

        /**
         * Update storage preference with the selected directory
         */
        private fun updateStoragePreference(
            context: Context,
            storageDirPref: Preference<String>,
        ) {
            UniFile.fromUri(context, storageDirPref.get().toUri())?.let {
                it.mkdir()
                storageDirPref.set("") // Trigger recompose
                storageDirPref.set(it.uri.toString())
            }
        }

        /**
         * Fallback to scoped storage if no other options are available
         */
        private fun fallbackToScopedStorage(
            context: Context,
            storageDirPref: Preference<String>,
        ) {
            val fallbackDir = File(context.getExternalFilesDir(null), context.stringResource(MR.strings.app_name))
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            storageDirPref.set("") // Trigger recompose
            storageDirPref.set(fallbackDir.toUri().toString())
            context.toast("Using default directory: ${fallbackDir.absolutePath}")
        }

        /**
         * Used to check if system is able to open contract [ActivityResultContracts.OpenDocumentTree]
         * by checking if intent [Intent.ACTION_OPEN_DOCUMENT_TREE] is available and not being stub (on Android TV)
         */
        private fun isIntentAvailable(context: Context, intent: Intent): Boolean {
            val packageManager = context.packageManager
            // Android TV: ResolveInfo{c236166 com.android.tv.frameworkpackagestubs/.Stubs$DocumentsStub m=0x108000 userHandle=UserHandle{0}}
            val resolveInfo = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return resolveInfo.any {
                it.activityInfo.packageName != null && it.activityInfo.packageName != "com.android.tv.frameworkpackagestubs"
            }
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
