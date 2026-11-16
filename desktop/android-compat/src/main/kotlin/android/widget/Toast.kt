package android.widget

import android.content.Context
import app.komikku.desktop.compat.DesktopLog
import app.komikku.desktop.compat.unsupported

/**
 * android-compat: `android.widget.Toast`. [show] hands the text to [presenter], which logs it by
 * default; the desktop UI can replace it (e.g. with a snackbar host).
 */
class Toast(
    @Suppress("UNUSED_PARAMETER") context: Context?,
) {

    var duration: Int = LENGTH_SHORT

    private var text: CharSequence = ""

    fun setText(s: CharSequence?) {
        text = s ?: ""
    }

    fun setText(resId: Int): Unit = unsupported("Toast.setText(resId)")

    fun show() {
        presenter(text, duration)
    }

    fun cancel() = Unit

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        /** Displays toasts on desktop. Called on the thread that calls [show]. */
        @Volatile
        @JvmStatic
        var presenter: (text: CharSequence, duration: Int) -> Unit = { text, _ ->
            DesktopLog.log(DesktopLog.INFO, "Toast", text.toString())
        }

        @JvmStatic
        fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast =
            Toast(context).apply {
                setText(text)
                this.duration = duration
            }

        @JvmStatic
        fun makeText(context: Context?, resId: Int, duration: Int): Toast = unsupported("Toast.makeText(resId)")
    }
}
