package android.text

/** android-compat: `android.text.Editable`, mutable plain text (no spans on desktop). */
interface Editable : CharSequence, Appendable {
    fun replace(st: Int, en: Int, text: CharSequence): Editable
    fun insert(where: Int, text: CharSequence): Editable
    fun delete(st: Int, en: Int): Editable
    fun clear()
    override fun append(text: CharSequence?): Editable
    override fun append(text: CharSequence?, start: Int, end: Int): Editable
    override fun append(text: Char): Editable
}
