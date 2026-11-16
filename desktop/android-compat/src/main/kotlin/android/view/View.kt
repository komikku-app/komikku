package android.view

import android.content.Context

/**
 * android-compat: `android.view.View`, reduced to the state a desktop renderer can map onto
 * Compose (enabled, visibility, tag). There is no view tree, layout or drawing on desktop.
 */
open class View(context: Context) {

    private val viewContext = context

    open val context: Context get() = viewContext

    open var isEnabled: Boolean = true

    open var visibility: Int = VISIBLE

    open var tag: Any? = null

    open var contentDescription: CharSequence? = null

    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
    }
}
