@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import java.io.Serializable

interface SChapter : Serializable {

    var url: String

    var name: String

    var date_upload: Long

    var chapter_number: Float

    var scanlator: String?

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
    }

    companion object {
        fun create(): SChapter {
            return SChapterImpl()
        }

        // SY -->
        operator fun invoke(
            name: String,
            url: String,
            date_upload: Long = 0,
            chapter_number: Float = -1F,
            scanlator: String? = null,
        ): SChapter {
            return create().apply {
                this.name = name
                this.url = url
                this.date_upload = date_upload
                this.chapter_number = chapter_number
                this.scanlator = scanlator
            }
        }
        // SY <--
    }
}
