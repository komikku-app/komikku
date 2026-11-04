package eu.kanade.tachiyomi.extension.model

/**
 * Identifies one extension line: a package signed by one key.
 *
 * Several stores can publish the same package name (e.g. a fork and the store it builds from), so the
 * package name alone does not say which store an installed extension can be updated from.
 */
val Extension.identityKey: String
    get() = "${pkgName}_$signatureHash"

/** Whether [other] is the same package, signed by the same key, as this extension. */
fun Extension.isSameExtensionAs(other: Extension): Boolean =
    pkgName == other.pkgName && signatureHash == other.signatureHash

/** Orders available extensions by version code, then by extension lib version. */
val AvailableExtensionVersionComparator: Comparator<Extension.Available> =
    compareBy<Extension.Available> { it.versionCode }.thenBy { it.libVersion }

/**
 * Returns the newest of [available] that can update this installed extension (same package name and
 * signing key), or null if no store lists it.
 */
fun Extension.Installed.findAvailable(available: Iterable<Extension.Available>): Extension.Available? =
    available
        .filter { isSameExtensionAs(it) }
        .maxWithOrNull(AvailableExtensionVersionComparator)

/** Whether [available] has a newer version or a newer extension lib than this installed extension. */
fun Extension.Installed.isOutdatedComparedTo(available: Extension.Available): Boolean =
    available.versionCode > versionCode || available.libVersion > libVersion
