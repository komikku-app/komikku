package android.widget

import android.content.Context
import android.text.TextWatcher
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList

/**
 * android-compat: `android.widget.TextView` as a text holder: text, hint, input type, error and
 * [TextWatcher]s (called by [setText] and by the desktop renderer when the user types).
 */
open class TextView(context: Context) : View(context) {

    private val watchers = CopyOnWriteArrayList<TextWatcher>()
    private var content: CharSequence = ""

    open var hint: CharSequence? = null

    /** `android.text.InputType` flags; the desktop renderer maps them to keyboard / masking options. */
    open var inputType: Int = 1 // InputType.TYPE_CLASS_TEXT

    open var error: CharSequence? = null

    open var maxLines: Int = Int.MAX_VALUE

    open fun getText(): CharSequence = content

    open fun setText(text: CharSequence?) {
        val old = content
        val new = text ?: ""
        watchers.forEach { it.beforeTextChanged(old, 0, old.length, new.length) }
        content = createContent(new)
        watchers.forEach { it.onTextChanged(content, 0, old.length, new.length) }
        afterTextChanged()
    }

    open fun setSingleLine() {
        setSingleLine(true)
    }

    open fun setSingleLine(singleLine: Boolean) {
        maxLines = if (singleLine) 1 else Int.MAX_VALUE
    }

    open fun addTextChangedListener(watcher: TextWatcher) {
        watchers.addIfAbsent(watcher)
    }

    open fun removeTextChangedListener(watcher: TextWatcher) {
        watchers.remove(watcher)
    }

    /** The stored text object; [EditText] keeps an `Editable`. */
    protected open fun createContent(text: CharSequence): CharSequence = text.toString()

    /** Called after the text changed; [EditText] passes its `Editable` to the watchers. */
    protected open fun afterTextChanged() {}

    protected fun textWatchers(): List<TextWatcher> = watchers
}
