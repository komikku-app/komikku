package eu.kanade.tachiyomi.ui.manga

import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.LocalSource

/**
 * KMK: a local entry's "Edit info" save ([MangaInfoEdits.localInfoEdit], Android's `MangaScreenModel.updateMangaInfo`
 * and the desktop details): the "Default" status keeps the entry's status instead of resetting it to Unknown in the
 * `ComicInfo.xml` written from the edited entry.
 */
class MangaInfoEditsTest {

    private val local = Manga.create().copy(
        id = 3,
        source = LocalSource.ID,
        url = "frieren-folder",
        ogTitle = "Frieren",
        ogStatus = SManga.COMPLETED.toLong(),
        lastUpdate = 5,
    )

    @Test
    fun `the default status keeps a local entry's status`() {
        val (edited, update) = MangaInfoEdits.localInfoEdit(local, "Frieren", "New author", null, null, null, null, null)
        edited.ogStatus shouldBe SManga.COMPLETED.toLong()
        edited.ogAuthor shouldBe "New author"
        update.status.shouldBeNull()
    }

    @Test
    fun `a chosen status replaces a local entry's status`() {
        val (edited, update) = MangaInfoEdits.localInfoEdit(local, null, null, null, null, null, null, SManga.ON_HIATUS.toLong())
        edited.ogStatus shouldBe SManga.ON_HIATUS.toLong()
        update.status shouldBe SManga.ON_HIATUS.toLong()
    }
}
