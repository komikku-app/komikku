package nl.adaptivity.xmlutil.core

import nl.adaptivity.xmlutil.XmlReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset

/**
 * android-compat: `nl.adaptivity.xmlutil.core.AndroidXmlReader` from xmlutil's `core-android`
 * artifact (Android's `XmlPullParser`). On desktop the same constructors read through xmlutil's
 * pure-Kotlin [KtXmlReader]. The `XmlPullParser` constructors are not provided.
 */
class AndroidXmlReader private constructor(
    delegate: XmlReader,
) : XmlReader by delegate {

    @JvmOverloads
    constructor(reader: Reader, relaxed: Boolean = false) : this(KtXmlReader(reader, relaxed))

    @JvmOverloads
    constructor(inputStream: InputStream, encoding: String?, relaxed: Boolean = false) : this(
        InputStreamReader(inputStream, encoding?.let(Charset::forName) ?: Charsets.UTF_8),
        relaxed,
    )
}
