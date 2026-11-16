package tachiyomi.core.common.storage

import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.File

class AndroidStorageFolderProvider(
    private val context: Context,
) : FolderProvider {

    /**
     * This will return File: /storage/emulated/0/<app_name>
     */
    override fun directory(): File {
        return File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                context.stringResource(MR.strings.app_name),
        )
    }

    /**
     * This will return: file:///storage/emulated/0/<app_name>
     */
    override fun path(): String {
        return directory().toUri().toString()
    }
}
