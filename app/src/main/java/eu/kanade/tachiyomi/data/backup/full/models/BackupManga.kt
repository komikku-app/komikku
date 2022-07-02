package eu.kanade.tachiyomi.data.backup.full.models

import eu.kanade.data.listOfStringsAndAdapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupManga(
    // in 1.x some of these values have different names
    @ProtoNumber(1) var source: Long,
    // url is called key in 1.x
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    // thumbnailUrl is called cover in 1.x
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    // @ProtoNumber(10) val customCover: String = "", 1.x value, not used in 0.x
    // @ProtoNumber(11) val lastUpdate: Long = 0, 1.x value, not used in 0.x
    // @ProtoNumber(12) val lastInit: Long = 0, 1.x value, not used in 0.x
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0, // Replaced by viewer_flags
    // @ProtoNumber(15) val flags: Int = 0, 1.x value, not used in 0.x
    @ProtoNumber(16) var chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    // Bump by 100 for values that are not saved/implemented in 1.x but are used in 0.x
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    @ProtoNumber(102) var brokenHistory: List<BrokenBackupHistory> = emptyList(),
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),

    // SY specific values
    @ProtoNumber(600) var mergedMangaReferences: List<BackupMergedMangaReference> = emptyList(),
    @ProtoNumber(601) var flatMetadata: BackupFlatMetadata? = null,
    @ProtoNumber(602) var customStatus: Int = 0,

    // J2K specific values
    @ProtoNumber(800) var customTitle: String? = null,
    @ProtoNumber(801) var customArtist: String? = null,
    @ProtoNumber(802) var customAuthor: String? = null,
    // skipping 803 due to using duplicate value in previous builds
    @ProtoNumber(804) var customDescription: String? = null,
    @ProtoNumber(805) var customGenre: List<String>? = null,

    // Neko specific values
    @ProtoNumber(901) var filtered_scanlators: String? = null,
) {
    fun getMangaImpl(): MangaImpl {
        return MangaImpl().apply {
            url = this@BackupManga.url
            title = this@BackupManga.title
            artist = this@BackupManga.artist
            author = this@BackupManga.author
            description = this@BackupManga.description
            genre = this@BackupManga.genre.joinToString()
            status = this@BackupManga.status
            thumbnail_url = this@BackupManga.thumbnailUrl
            favorite = this@BackupManga.favorite
            source = this@BackupManga.source
            date_added = this@BackupManga.dateAdded
            viewer_flags = this@BackupManga.viewer_flags ?: this@BackupManga.viewer
            chapter_flags = this@BackupManga.chapterFlags
            filtered_scanlators = this@BackupManga.filtered_scanlators
        }
    }

    fun getChaptersImpl(): List<ChapterImpl> {
        return chapters.map {
            it.toChapterImpl()
        }
    }

    // SY -->
    fun getCustomMangaInfo(): CustomMangaManager.MangaJson? {
        if (customTitle != null ||
            customArtist != null ||
            customAuthor != null ||
            customDescription != null ||
            customGenre != null ||
            customStatus != 0
        ) {
            return CustomMangaManager.MangaJson(
                id = 0L,
                title = customTitle,
                author = customAuthor,
                artist = customArtist,
                description = customDescription,
                genre = customGenre,
                status = customStatus.takeUnless { it == 0 }?.toLong(),
            )
        }
        return null
    }
    // SY <--

    fun getTrackingImpl(): List<TrackImpl> {
        return tracking.map {
            it.getTrackingImpl()
        }
    }

    companion object {
        fun copyFrom(manga: Manga /* SY --> */, customMangaManager: CustomMangaManager?/* SY <-- */): BackupManga {
            return BackupManga(
                url = manga.url,
                // SY -->
                title = manga.ogTitle,
                artist = manga.ogArtist,
                author = manga.ogAuthor,
                description = manga.ogDescription,
                genre = manga.ogGenre ?: emptyList(),
                status = manga.ogStatus.toInt(),
                // SY <--
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                source = manga.source,
                dateAdded = manga.dateAdded,
                viewer = (manga.viewerFlags.toInt() and ReadingModeType.MASK),
                viewer_flags = manga.viewerFlags.toInt(),
                chapterFlags = manga.chapterFlags.toInt(),
                // SY -->
                filtered_scanlators = manga.filteredScanlators?.let(listOfStringsAndAdapter::encode),
            ).also { backupManga ->
                customMangaManager?.getManga(manga.id)?.let {
                    backupManga.customTitle = it.title
                    backupManga.customArtist = it.artist
                    backupManga.customAuthor = it.author
                    backupManga.customDescription = it.description
                    backupManga.customGenre = it.genre
                    backupManga.customStatus = it.status?.toInt() ?: 0
                }
            }
            // SY <--
        }
    }
}
