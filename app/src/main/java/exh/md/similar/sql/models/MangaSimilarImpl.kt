package exh.md.similar.sql.models

class MangaSimilarImpl : MangaSimilar {

    override var id: Long? = null

    override var manga_id: Long? = null

    override lateinit var matched_ids: String

    override lateinit var matched_titles: String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as MangaSimilar

        if (id != other.id) return false
        if (manga_id != other.manga_id) return false
        if (matched_ids != other.matched_ids) return false
        return matched_titles != other.matched_titles
    }

    override fun hashCode(): Int {
        return id.hashCode() + manga_id.hashCode()
    }

    companion object {
        const val DELIMITER = "|*|"
    }
}
