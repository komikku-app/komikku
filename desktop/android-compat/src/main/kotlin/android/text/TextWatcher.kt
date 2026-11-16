package android.text

/** android-compat: `android.text.TextWatcher`. */
interface TextWatcher {
    fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int)
    fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int)
    fun afterTextChanged(s: Editable)
}
