package app.komikku.desktop.ui.manga

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.manga.MangaInfoEdits
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.LocalSource

/**
 * P10: the shared part of SY's "Edit info" ([MangaInfoEdits], also called by Android's `MangaScreenModel`), on entries
 * outside the library (no custom info is read for them, so `title` = `ogTitle` and so on).
 */
class MangaInfoEditsTest {

    private val source = Manga.create().copy(
        id = 7,
        source = 1234,
        url = "/frieren",
        ogTitle = "Frieren",
        ogAuthor = "Yamada",
        ogGenre = listOf("Fantasy"),
        ogStatus = SManga.ONGOING.toLong(),
        lastUpdate = 10,
    )
    private val local = source.copy(source = LocalSource.ID, url = "frieren-folder", ogTitle = "frieren-folder")

    @Test
    fun `a source entry starts with empty fields, its tags and the default status`() {
        val fields = MangaInfoEdits.initialFields(source)
        fields.title shouldBe ""
        fields.author shouldBe ""
        fields.tags shouldBe listOf("Fantasy")
        fields.statusIndex shouldBe 0
    }

    @Test
    fun `a local entry starts with its values, the folder name as title stays empty`() {
        val fields = MangaInfoEdits.initialFields(local)
        fields.title shouldBe ""
        fields.author shouldBe "Yamada"
        MangaInfoEdits.initialFields(local.copy(ogTitle = "Frieren")).title shouldBe "Frieren"
        MangaInfoEdits.resetTags(local) shouldBe emptyList()
        MangaInfoEdits.resetTags(source) shouldBe listOf("Fantasy")
    }

    @Test
    fun `custom info keeps only what differs from the source`() {
        val info = MangaInfoEdits.customInfoEdit(source, " Sousou ", "", "  ", null, "Text", listOf("Fantasy"), SManga.ONGOING.toLong())
        info.id shouldBe 7
        info.title shouldBe "Sousou"
        info.author.shouldBeNull()
        info.artist.shouldBeNull()
        info.description shouldBe "Text"
        info.genre.shouldBeNull()
        info.status.shouldBeNull()
        val other = MangaInfoEdits.customInfoEdit(source, null, null, null, null, null, listOf("Magic"), MangaInfoEdits.statusAt(2))
        other.genre shouldBe listOf("Magic")
        other.status shouldBe SManga.COMPLETED.toLong()
        MangaInfoEdits.statusAt(0).shouldBeNull()
    }

    @Test
    fun `a local edit changes the source values, a blank title falls back to the folder`() {
        val (edited, update) = MangaInfoEdits.localInfoEdit(local, " ", " New author ", "", null, null, emptyList(), null)
        edited.ogTitle shouldBe "frieren-folder"
        edited.ogAuthor shouldBe "New author"
        edited.ogArtist.shouldBeNull()
        edited.ogGenre.shouldBeNull()
        edited.ogStatus shouldBe 0
        edited.lastUpdate shouldBe 11
        update.id shouldBe 7
        update.title shouldBe "frieren-folder"
        update.author shouldBe "New author"
        update.genre shouldBe emptyList()
        update.status.shouldBeNull()
    }
}
