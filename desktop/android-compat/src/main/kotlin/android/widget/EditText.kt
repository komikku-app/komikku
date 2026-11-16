package android.widget

import android.content.Context
import android.text.Editable
import android.text.SpannableStringBuilder

/**
 * android-compat: `android.widget.EditText`. Used by `EditTextPreference.OnBindEditTextListener`;
 * the desktop renderer calls [setText] as the user types, so validating [TextWatcher][android.text.TextWatcher]s
 * see every change.
 */
open class EditText(context: Context) : TextView(context) {

    open var selectionStart: Int = 0
        protected set
    open var selectionEnd: Int = 0
        protected set

    override fun getText(): Editable = super.getText() as Editable

    override fun createContent(text: CharSequence): CharSequence = SpannableStringBuilder(text)

    override fun afterTextChanged() {
        val editable = getText()
        textWatchers().forEach { it.afterTextChanged(editable) }
    }

    open fun setSelection(index: Int) {
        setSelection(index, index)
    }

    open fun setSelection(start: Int, stop: Int) {
        val length = getText().length
        require(start in 0..length && stop in 0..length) { "setSelection($start, $stop) out of range for length $length" }
        selectionStart = start
        selectionEnd = stop
    }

    open fun selectAll() {
        setSelection(0, getText().length)
    }

    init {
        setText("")
    }
}
