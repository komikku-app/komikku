package sample.main

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient.CustomViewCallback
import java.lang.ref.WeakReference

/**
 * Created by Edsuns@qq.com on 2021/4/4.
 */
object Fullscreen {

    private var customView: WeakReference<View>? = null
    private var viewCallback: CustomViewCallback? = null

    fun onShowCustomView(context: Context, view: View, callback: CustomViewCallback) {
        if (customView?.get() != null) {
            callback.onCustomViewHidden()
            return
        }
        val activity = getActivity(context) ?: return
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        customView = WeakReference(view)
        viewCallback = callback
        setImmersiveMode(activity, true)
        view.setBackgroundColor(Color.BLACK)
        activity.addContentView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun onHideCustomView() {
        val view = customView?.get() ?: return
        val activity = getActivity(view.context) ?: return
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setImmersiveMode(activity, false)
        val viewGroup = view.parent as ViewGroup
        viewGroup.removeView(view)
        viewCallback?.onCustomViewHidden()
        viewCallback = null
        customView = null
    }

    private fun getActivity(context: Context?): Activity? {
        if (context == null) return null
        if (context is Activity) return context
        return if (context is ContextWrapper) getActivity(context.baseContext) else null
    }

    private fun setImmersiveMode(activity: Activity, enable: Boolean) {
        var flags = activity.window.decorView.systemUiVisibility
        val immersiveModeFlags = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        flags = if (enable) {
            flags or immersiveModeFlags
        } else {
            flags and immersiveModeFlags.inv()
        }
        activity.window.decorView.systemUiVisibility = flags
    }
}
