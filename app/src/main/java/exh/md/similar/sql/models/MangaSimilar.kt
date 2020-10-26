package exh.md.similar.sql.models

import java.io.Serializable

/**
 * Object containing the history statistics of a chapter
 */
interface MangaSimilar : Serializable {

    /**
     * Id of this similar manga object.
     */
    var id: Long?

    /**
     * Id of matching manga
     */
    var manga_id: Long?

    /**
     * JSONArray.toString() list of ids for this manga
     * Example: [3467, 5907, 21052, 2141, 6139, 5602, 3999]
     */
    var matched_ids: String

    /**
     * JSONArray.toString() list of titles for this manga
     * Example: [Title1, Title2, ..., Title10]
     */
    var matched_titles: String
}
