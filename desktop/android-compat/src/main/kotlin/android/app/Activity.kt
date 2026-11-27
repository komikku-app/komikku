package android.app

import android.content.ContextWrapper
import android.os.Bundle

/**
 * android-compat: `android.app.Activity`, for extensions' types only: the desktop app has no activities, so none is
 * ever created by it. Extensions declare activities (Keiyoushi's `UrlActivity` for deep links) and track the current
 * one through [Application.ActivityLifecycleCallbacks]; their code merges activities with the [Application] as
 * `ContextWrapper`s, which the JVM verifies against this class (`Activity` → `ContextThemeWrapper` → `ContextWrapper`
 * on Android).
 */
open class Activity : ContextWrapper(null) {

    private var finishing = false

    open fun getApplication(): Application? = null

    protected open fun onCreate(savedInstanceState: Bundle?) {}

    open fun finish() {
        finishing = true
    }

    open fun isFinishing(): Boolean = finishing

    open fun isDestroyed(): Boolean = false
}
