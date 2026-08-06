package eu.kanade.tachiyomi.util.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.IntentCompat
import eu.kanade.tachiyomi.util.storage.getUriCompat
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.File
import java.io.Serializable

fun Uri.toShareIntent(context: Context, type: String = "image/*", message: String? = null): Intent {
    val uri = this

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        // KMK --> URI yang benar-benar di-grant: untuk file:// (hasil resolve raw path),
        // konversi ke FileProvider agar bisa di-share lintas-app di Android 7+.
        val streamUri: Uri? = when (uri.scheme) {
            "http", "https" -> null
            "file" -> File(uri.path!!).getUriCompat(context)
            else -> uri
        }
        // KMK <--
        when (uri.scheme) {
            "http", "https" -> {
                putExtra(Intent.EXTRA_TEXT, uri.toString())
            }
            "content" -> {
                message?.let { putExtra(Intent.EXTRA_TEXT, it) }
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            // KMK -->
            "file" -> {
                message?.let { putExtra(Intent.EXTRA_TEXT, it) }
                putExtra(Intent.EXTRA_STREAM, streamUri)
            }
            // KMK <--
        }
        if (streamUri != null) {
            clipData = ClipData.newRawUri(null, streamUri)
        }
        setType(type)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    return Intent.createChooser(shareIntent, context.stringResource(MR.strings.action_share)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}

inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? {
    return IntentCompat.getParcelableExtra(this, name, T::class.java)
}

inline fun <reified T : Serializable> Intent.getSerializableExtraCompat(name: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializableExtra(name) as? T
    }
}
