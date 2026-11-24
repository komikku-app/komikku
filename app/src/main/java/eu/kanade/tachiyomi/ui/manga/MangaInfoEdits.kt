package eu.kanade.tachiyomi.ui.manga

import eu.kanade.tachiyomi.source.model.SManga
import exh.util.dropBlank
import exh.util.nullIfEmpty
import exh.util.trimOrNull
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.source.local.isLocal

/**
 * KMK: the platform-neutral part of SY's "Edit info" (`EditMangaDialog` and `MangaScreenModel.updateMangaInfo`), shared
 * with the desktop details (P10, `app-shared/borrowed-app-sources.gradle.kts`): which values the dialog starts with,
 * the status choices, the tags of "Reset tags", and what a save stores. Android's dialog (Android views) keeps its own
 * widgets; its screen model builds what it stores with [localInfoEdit] / [customInfoEdit].
 */
object MangaInfoEdits {

    /** The status choices of the dialog in order: "Default" (the source's status), then the `SManga` statuses. */
    val statusChoices: List<Int?> = listOf(
        null,
        SManga.ONGOING,
        SManga.COMPLETED,
        SManga.LICENSED,
        SManga.PUBLISHING_FINISHED,
        SManga.CANCELLED,
        SManga.ON_HIATUS,
    )

    /** The fields the dialog opens with (`EditMangaDialog.onViewCreated`): only edited values for a source entry. */
    data class Fields(
        val title: String,
        val author: String,
        val artist: String,
        val thumbnailUrl: String,
        val description: String,
        val tags: List<String>,
        /** Index in [statusChoices]. */
        val statusIndex: Int,
    )

    fun initialFields(manga: Manga): Fields {
        val statusIndex = if (manga.status != manga.ogStatus) {
            when (manga.status.toInt()) {
                SManga.UNKNOWN -> 0
                SManga.ONGOING -> 1
                SManga.COMPLETED -> 2
                SManga.LICENSED -> 3
                SManga.PUBLISHING_FINISHED, 61 -> 4
                SManga.CANCELLED, 62 -> 5
                SManga.ON_HIATUS, 63 -> 6
                else -> 0
            }
        } else {
            0
        }
        val tags = manga.genre.orEmpty().dropBlank()
        return if (manga.isLocal()) {
            Fields(
                title = if (manga.title != manga.url) manga.title else "",
                author = manga.author.orEmpty(),
                artist = manga.artist.orEmpty(),
                thumbnailUrl = manga.thumbnailUrl.orEmpty(),
                description = manga.description.orEmpty(),
                tags = tags,
                statusIndex = statusIndex,
            )
        } else {
            Fields(
                title = if (manga.title != manga.ogTitle) manga.title else "",
                author = if (manga.author != manga.ogAuthor) manga.author.orEmpty() else "",
                artist = if (manga.artist != manga.ogArtist) manga.artist.orEmpty() else "",
                thumbnailUrl = if (manga.thumbnailUrl != manga.ogThumbnailUrl) manga.thumbnailUrl.orEmpty() else "",
                description = if (manga.description != manga.ogDescription) manga.description.orEmpty() else "",
                tags = tags,
                statusIndex = statusIndex,
            )
        }
    }

    /** "Reset tags" (`EditMangaDialog.resetTags`): the source's tags, none for a local entry or one without tags. */
    fun resetTags(manga: Manga): List<String> =
        if (manga.genre.isNullOrEmpty() || manga.isLocal()) emptyList() else manga.ogGenre.orEmpty()

    /** The status of the choice at [index] in [statusChoices], `null` for "Default". */
    fun statusAt(index: Int): Long? = statusChoices.getOrNull(index)?.toLong()

    /**
     * A local entry's edit (`updateMangaInfo`, local branch): the entry with the new source values (to write the
     * local `ComicInfo.xml` from and to show) and the database update. A blank title falls back to the folder name.
     */
    fun localInfoEdit(
        manga: Manga,
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ): Pair<Manga, MangaUpdate> {
        val newTitle = if (title.isNullOrBlank()) manga.url else title.trim()
        val newAuthor = author?.trimOrNull()
        val newArtist = artist?.trimOrNull()
        val newThumbnailUrl = thumbnailUrl?.trimOrNull()
        val newDesc = description?.trimOrNull()
        val edited = manga.copy(
            ogTitle = newTitle,
            ogAuthor = newAuthor,
            ogArtist = newArtist,
            ogThumbnailUrl = newThumbnailUrl,
            ogDescription = newDesc,
            ogGenre = tags?.nullIfEmpty(),
            ogStatus = status ?: 0,
            lastUpdate = manga.lastUpdate + 1,
        )
        val update = MangaUpdate(
            manga.id,
            title = newTitle,
            author = newAuthor,
            artist = newArtist,
            thumbnailUrl = newThumbnailUrl,
            description = newDesc,
            genre = tags,
            status = status,
        )
        return edited to update
    }

    /**
     * A source entry's edit (`updateMangaInfo`, other branch): the custom info to store (blank = the source's value;
     * tags only when they differ from the source's, the status only when it differs). All `null` removes the entry's
     * custom info (`CustomMangaRepository.set`).
     */
    fun customInfoEdit(
        manga: Manga,
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ): CustomMangaInfo {
        val genre = if (!tags.isNullOrEmpty() && tags != manga.ogGenre) tags else null
        return CustomMangaInfo(
            manga.id,
            title?.trimOrNull(),
            author?.trimOrNull(),
            artist?.trimOrNull(),
            thumbnailUrl?.trimOrNull(),
            description?.trimOrNull(),
            genre,
            status.takeUnless { it == manga.ogStatus },
        )
    }
}
