package androidx.preference

import android.content.Context
import android.widget.EditText
import app.komikku.desktop.compat.NOT_SET

/**
 * android-compat: AndroidX `EditTextPreference`, a string value edited in a dialog.
 *
 * [setOnBindEditTextListener] is stored for the desktop renderer, which can bind a compat
 * [EditText] (input type, hint, text watchers for validation) before showing its text field.
 */
open class EditTextPreference(context: Context) : DialogPreference(context) {

    /** Lets the caller configure the dialog's text field (input type, validation, …). */
    fun interface OnBindEditTextListener {
        fun onBindEditText(editText: EditText)
    }

    /** Summary = current text, or "Not set" when empty. */
    class SimpleSummaryProvider private constructor() : SummaryProvider<EditTextPreference> {
        override fun provideSummary(preference: EditTextPreference): CharSequence? =
            preference.text.takeUnless { it.isNullOrEmpty() } ?: NOT_SET

        companion object {
            private val singleton by lazy { SimpleSummaryProvider() }

            @JvmStatic
            fun getInstance(): SimpleSummaryProvider = singleton
        }
    }

    private var currentText: String? = null
    private var bindListener: OnBindEditTextListener? = null

    open var text: String?
        get() = currentText
        set(value) {
            val wasBlocking = shouldDisableDependents()
            currentText = value
            persistString(value)
            val isBlocking = shouldDisableDependents()
            if (isBlocking != wasBlocking) notifyDependencyChange(isBlocking)
            notifyChanged()
        }

    open fun setOnBindEditTextListener(onBindEditTextListener: OnBindEditTextListener?) {
        bindListener = onBindEditTextListener
    }

    /** Package-private in AndroidX; public here so the desktop renderer can apply it. */
    open fun getOnBindEditTextListener(): OnBindEditTextListener? = bindListener

    override fun shouldDisableDependents(): Boolean = currentText.isNullOrEmpty() || super.shouldDisableDependents()

    override fun onSetInitialValue(defaultValue: Any?) {
        text = getPersistedString(defaultValue as String?)
    }
}
