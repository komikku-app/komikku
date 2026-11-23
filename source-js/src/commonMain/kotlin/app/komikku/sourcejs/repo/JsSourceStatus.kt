package app.komikku.sourcejs.repo

import app.komikku.sourcejs.JsSourceDescriptor

/** Where an installed JavaScript source stands against what its repositories offer (no auto-update). */
enum class JsSourceStatus {
    /** The best offer is not newer and, as far as known, serves the installed text. */
    UP_TO_DATE,

    /** A repository offers a newer version. */
    UPDATE_AVAILABLE,

    /** The best offer has the installed version, but its code URL now serves a different text (another SHA-256). */
    TEXT_CHANGED,

    /** No repository lists the source any more (it stays installed and usable). */
    OBSOLETE,
    ;

    companion object {
        /**
         * The status of an installed source with [installedVersion] and [installedSha256], given the best [offer] for
         * its id (`null`: not listed) and the SHA-256 of the text the offer's code URL serves now ([offerSha256],
         * `null` when not checked). [listingKnown] is false while the repository it came from could not be read, so
         * a missing offer is not taken as obsolete.
         */
        fun of(
            installedVersion: String,
            installedSha256: String,
            offer: JsSourceDescriptor?,
            offerSha256: String?,
            listingKnown: Boolean = true,
        ): JsSourceStatus = when {
            offer == null -> if (listingKnown) OBSOLETE else UP_TO_DATE
            MangayomiVersions.compare(installedVersion, offer.version) < 0 -> UPDATE_AVAILABLE
            MangayomiVersions.compare(installedVersion, offer.version) == 0 &&
                offerSha256 != null && !offerSha256.equals(installedSha256, ignoreCase = true) -> TEXT_CHANGED
            else -> UP_TO_DATE
        }
    }
}

/** An index entry together with the repository (index URL) that offers it. */
data class JsSourceOffer(val descriptor: JsSourceDescriptor, val repoUrl: String) {
    val id: Long get() = descriptor.id

    companion object {
        /**
         * One offer per id from the indexes of [byRepository] (in repository order): the highest version wins, the
         * earlier repository on a tie (Mangayomi treats the same id as the same source across repositories).
         */
        fun merge(byRepository: List<Pair<String, List<JsSourceDescriptor>>>): List<JsSourceOffer> {
            val best = LinkedHashMap<Long, JsSourceOffer>()
            byRepository.forEach { (repoUrl, entries) ->
                entries.forEach { entry ->
                    val other = best[entry.id]
                    if (other == null || MangayomiVersions.compare(entry.version, other.descriptor.version) > 0) {
                        best[entry.id] = JsSourceOffer(entry, repoUrl)
                    }
                }
            }
            return best.values.toList()
        }
    }
}
