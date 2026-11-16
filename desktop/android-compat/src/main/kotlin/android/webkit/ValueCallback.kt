package android.webkit

/** android-compat: `android.webkit.ValueCallback`. */
fun interface ValueCallback<T> {
    fun onReceiveValue(value: T)
}
