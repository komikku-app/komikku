@file:JvmName("HtmlKt")

package androidx.core.text

import android.text.Html
import android.text.Spanned

/** android-compat: AndroidX core-ktx `String.parseAsHtml()` (no image getter / tag handler). */
fun String.parseAsHtml(flags: Int = Html.FROM_HTML_MODE_LEGACY): Spanned = Html.fromHtml(this, flags)
