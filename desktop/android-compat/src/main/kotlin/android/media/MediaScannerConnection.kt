package android.media

import android.content.Context
import android.net.Uri

/** android-compat: `android.media.MediaScannerConnection`. There is no media index on desktop. */
class MediaScannerConnection private constructor() {

    fun interface OnScanCompletedListener {
        fun onScanCompleted(path: String, uri: Uri?)
    }

    companion object {
        /** No-op; the callback is invoked for every path with a `null` uri, as for unscannable files. */
        @JvmStatic
        fun scanFile(
            context: Context,
            paths: Array<out String?>,
            mimeTypes: Array<out String?>?,
            callback: OnScanCompletedListener?,
        ) {
            if (callback == null) return
            paths.filterNotNull().forEach { callback.onScanCompleted(it, null) }
        }
    }
}
