package com.hippo.unifile

import android.content.Context
import android.net.Uri

/** android-compat: UniFile's `UriHandler`, consulted by [UniFile.fromUri] for non-file URIs. */
fun interface UriHandler {
    fun fromUri(context: Context, uri: Uri): UniFile?
}
