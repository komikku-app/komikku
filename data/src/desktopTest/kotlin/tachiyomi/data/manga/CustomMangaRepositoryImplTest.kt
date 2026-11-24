package tachiyomi.data.manga

import android.app.Application
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.manga.model.CustomMangaInfo
import java.io.File

/**
 * KMK: SY's custom manga info store (`edits.json`): what is set survives a restart (a new repository on the same
 * file), and so does removing it, also when the last entry's info is removed (SY left the old file in place).
 */
class CustomMangaRepositoryImplTest {

    @TempDir
    lateinit var tempDir: File

    private fun repository() = CustomMangaRepositoryImpl(Application(tempDir))

    private fun info(id: Long, title: String?) = CustomMangaInfo(id, title, null, null, null, null, null, null)

    @Test
    fun `custom info is kept across restarts`() {
        repository().set(info(1, "One"))
        repository().get(1)?.title shouldBe "One"
    }

    @Test
    fun `removing the last custom info is kept across restarts`() {
        repository().apply {
            set(info(1, "One"))
            set(info(2, "Two"))
        }
        repository().set(info(1, null))
        repository().get(1).shouldBeNull()
        repository().get(2)?.title shouldBe "Two"

        repository().set(info(2, null))
        repository().get(2).shouldBeNull()
    }
}
