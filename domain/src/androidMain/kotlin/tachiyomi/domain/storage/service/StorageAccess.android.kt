package tachiyomi.domain.storage.service

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import java.io.File

/**
 * Call FilePicker to allow access to storage or request All Files Access Permission if not available.
 *
 * KMK: Android part of `StorageManager.allowAccessStorage`; this function and the private helpers
 * below were moved verbatim from the KMK block of StorageManager's companion object
 * (docs/kmp-desktop/PROGRESS.md, 3.1).
 */
internal actual fun allowAccessStoragePlatform(
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
    if (hasManageExternalStoragePermission(context)) {
        updateStoragePreference(context, storageDirPref)
    } else {
        requestManageExternalStoragePermission(context)
    }
}

private fun hasManageExternalStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
}

private fun requestManageExternalStoragePermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:${context.packageName}".toUri()
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            1001,
        )
    } else {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            1001,
        )
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
