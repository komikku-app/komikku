package com.hippo.unifile

/** android-compat: UniFile's `FilenameFilter`. */
fun interface FilenameFilter {
    fun accept(dir: UniFile, filename: String): Boolean
}
