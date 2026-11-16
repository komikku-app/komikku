package app.komikku.desktop.extension.apk

/**
 * The parts of an APK's `AndroidManifest.xml` the extension loader needs, read from the binary
 * manifest (see [BinaryXml]).
 */
data class ApkManifest(
    val packageName: String,
    val versionName: String?,
    /** `versionCode` combined with `versionCodeMajor`, like `PackageInfoCompat.getLongVersionCode`. */
    val versionCode: Long,
    /** `<application android:label>` when it is a literal string. */
    val label: String?,
    /** `<application android:label>` when it is a resource reference. */
    val labelResId: Int?,
    /** `<application android:icon>` resource reference. */
    val iconResId: Int?,
    /** Names of the `<uses-feature>` elements. */
    val usesFeatures: List<String>,
    /** `<application>`'s `<meta-data>`, typed like the platform's `ApplicationInfo.metaData` bundle. */
    val metaData: ApkMetaData,
) {
    companion object {
        // Framework attribute resource ids (android.R.attr)
        private const val ATTR_LABEL = 0x01010001
        private const val ATTR_ICON = 0x01010002
        private const val ATTR_NAME = 0x01010003
        private const val ATTR_VALUE = 0x01010024
        private const val ATTR_RESOURCE = 0x01010025
        private const val ATTR_VERSION_CODE = 0x0101021b
        private const val ATTR_VERSION_NAME = 0x0101021c
        private const val ATTR_VERSION_CODE_MAJOR = 0x01010576

        /** Parses a binary `AndroidManifest.xml`. */
        fun parse(binaryXml: ByteArray): ApkManifest {
            val manifest = BinaryXml.parse(binaryXml)
            if (manifest.name != "manifest") throw BinaryResourceException("Root element is <${manifest.name}>")

            val packageName = manifest.plainAttribute("package")?.stringValue
                ?: throw BinaryResourceException("Manifest without package name")
            val versionCode = manifest.androidAttribute(ATTR_VERSION_CODE, "versionCode")?.intValue() ?: 0
            val versionCodeMajor = manifest.androidAttribute(ATTR_VERSION_CODE_MAJOR, "versionCodeMajor")?.intValue() ?: 0
            val versionName = manifest.androidAttribute(ATTR_VERSION_NAME, "versionName")?.stringValue

            val application = manifest.child("application")
            val label = application?.androidAttribute(ATTR_LABEL, "label")
            val icon = application?.androidAttribute(ATTR_ICON, "icon")

            val metaData = application?.children("meta-data").orEmpty().mapNotNull { element ->
                val name = element.androidAttribute(ATTR_NAME, "name")?.stringValue ?: return@mapNotNull null
                val value = element.androidAttribute(ATTR_VALUE, "value")?.toMetaValue()
                    ?: element.androidAttribute(ATTR_RESOURCE, "resource")?.let { MetaValue.Resource(it.data) }
                    ?: return@mapNotNull null
                name to value
            }.toMap()

            return ApkManifest(
                packageName = packageName,
                versionName = versionName,
                versionCode = (versionCodeMajor.toLong() shl 32) or (versionCode.toLong() and 0xffffffffL),
                label = label?.takeIf { it.type != ResValue.TYPE_REFERENCE }?.stringValue,
                labelResId = label?.takeIf { it.type == ResValue.TYPE_REFERENCE }?.data,
                iconResId = icon?.takeIf { it.type == ResValue.TYPE_REFERENCE }?.data,
                usesFeatures = manifest.children("uses-feature").mapNotNull {
                    it.androidAttribute(ATTR_NAME, "name")?.stringValue
                },
                metaData = ApkMetaData(metaData),
            )
        }

        private fun XmlAttribute.intValue(): Int? = when (type) {
            in ResValue.TYPE_FIRST_INT..ResValue.TYPE_LAST_INT -> data
            else -> rawValue?.toIntOrNull()
        }

        /** Same mapping as the platform's manifest parser does for `<meta-data android:value>`. */
        private fun XmlAttribute.toMetaValue(): MetaValue? = when (type) {
            ResValue.TYPE_STRING -> rawValue?.let(MetaValue::Text)
            ResValue.TYPE_INT_BOOLEAN -> MetaValue.Bool(data != 0)
            in ResValue.TYPE_FIRST_INT..ResValue.TYPE_LAST_INT -> MetaValue.Integer(data)
            ResValue.TYPE_FLOAT -> MetaValue.Decimal(Float.fromBits(data))
            ResValue.TYPE_REFERENCE -> MetaValue.Resource(data)
            ResValue.TYPE_NULL -> rawValue?.let(MetaValue::Text)
            else -> rawValue?.let(MetaValue::Text)
        }
    }
}

/** A typed `<meta-data>` value. */
sealed interface MetaValue {
    data class Text(val value: String) : MetaValue
    data class Integer(val value: Int) : MetaValue
    data class Decimal(val value: Float) : MetaValue
    data class Bool(val value: Boolean) : MetaValue
    data class Resource(val id: Int) : MetaValue
}

/**
 * The `<meta-data>` of an application. The getters behave like `android.os.Bundle`'s for a value of
 * another type (`null` / `0` / `0f`), which is what `ExtensionLoader` relies on.
 */
class ApkMetaData(val values: Map<String, MetaValue>) {

    fun getString(key: String): String? = (values[key] as? MetaValue.Text)?.value

    fun getInt(key: String): Int = (values[key] as? MetaValue.Integer)?.value ?: 0

    fun getFloat(key: String): Float = (values[key] as? MetaValue.Decimal)?.value ?: 0f

    fun getBoolean(key: String): Boolean = (values[key] as? MetaValue.Bool)?.value ?: false

    override fun toString(): String = values.toString()
}
