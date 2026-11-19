package eu.kanade.tachiyomi.data.sync.service

import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class SyncServiceMergeTest {

    private val syncPreferences = mockk<SyncPreferences> {
        every { uniqueDeviceID() } returns "device"
    }

    private val service = object : SyncService(mockk(), Json, syncPreferences) {
        override suspend fun doSync(syncData: SyncData): Backup? = null

        fun merge(local: Backup, remote: Backup) = mergeSyncData(SyncData(backup = local), SyncData(backup = remote))
    }

    private val categories = listOf(BackupCategory(name = "Reading", order = 1))

    private fun manga(
        favorite: Boolean,
        version: Long,
        favoriteModifiedAt: Long?,
        categories: List<Long> = emptyList(),
        dateAdded: Long = 0,
    ) = BackupManga(source = 1, url = "/manga/1", title = "Manga").apply {
        this.favorite = favorite
        this.version = version
        this.favoriteModifiedAt = favoriteModifiedAt
        this.categories = categories
        this.dateAdded = dateAdded
    }

    private fun mergedManga(local: BackupManga, remote: BackupManga): BackupManga {
        val merged = service.merge(
            local = Backup(backupManga = listOf(local), backupCategories = categories),
            remote = Backup(backupManga = listOf(remote), backupCategories = categories),
        )
        return merged.backup!!.backupManga.single()
    }

    @Test
    fun `a removal survives a later library update on the other device`() {
        // The other device refreshed the entry after this one removed it, so its version is higher
        val local = manga(favorite = false, version = 3, favoriteModifiedAt = 200)
        val remote = manga(favorite = true, version = 5, favoriteModifiedAt = 100, categories = listOf(1))

        val merged = mergedManga(local, remote)

        merged.favorite shouldBe false
        merged.favoriteModifiedAt shouldBe 200
        merged.version shouldBe 5
    }

    @Test
    fun `a re-add on the other device wins over an older removal with a higher version`() {
        val local = manga(favorite = false, version = 5, favoriteModifiedAt = 100)
        val remote = manga(favorite = true, version = 3, favoriteModifiedAt = 200, categories = listOf(1), dateAdded = 42)

        val merged = mergedManga(local, remote)

        merged.favorite shouldBe true
        merged.favoriteModifiedAt shouldBe 200
        merged.dateAdded shouldBe 42
        merged.categories shouldContainExactly listOf(1L)
    }

    @Test
    fun `the newer version keeps its library state when it also changed it last`() {
        val local = manga(favorite = true, version = 5, favoriteModifiedAt = 200, categories = listOf(1))
        val remote = manga(favorite = false, version = 3, favoriteModifiedAt = 100)

        val merged = mergedManga(local, remote)

        merged.favorite shouldBe true
        merged.categories shouldContainExactly listOf(1L)
    }

    @Test
    fun `a missing library change time counts as the oldest`() {
        val local = manga(favorite = true, version = 5, favoriteModifiedAt = null)
        val remote = manga(favorite = false, version = 3, favoriteModifiedAt = 100)

        mergedManga(local, remote).favorite shouldBe false
    }
}
