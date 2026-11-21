package eu.kanade.tachiyomi.data.coil

import eu.kanade.tachiyomi.data.coil.MangaCoverMetadata.needsColorUpdate
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.MangaCover

class MangaCoverMetadataTest {

    @Test
    fun `non-favorite manga needs vibrant color once, if wanted or forced`() {
        val noColor = cover(favorite = false)
        needsColorUpdate(noColor, onlyDominantColor = false) shouldBe true
        needsColorUpdate(noColor, onlyDominantColor = true) shouldBe false
        needsColorUpdate(noColor, onlyDominantColor = true, force = true) shouldBe true

        val withVibrant = cover(favorite = false, vibrant = true)
        needsColorUpdate(withVibrant, onlyDominantColor = false) shouldBe false
        needsColorUpdate(withVibrant, onlyDominantColor = false, force = true) shouldBe false
    }

    @Test
    fun `favorite manga needs only dominant color once, even if forced`() {
        needsColorUpdate(cover(favorite = true), onlyDominantColor = true) shouldBe true
        needsColorUpdate(cover(favorite = true, vibrant = true), onlyDominantColor = true) shouldBe true

        val withDominant = cover(favorite = true, dominant = true)
        needsColorUpdate(withDominant, onlyDominantColor = true) shouldBe false
        needsColorUpdate(withDominant, onlyDominantColor = true, force = true) shouldBe false
    }

    @Test
    fun `favorite manga needs both colors until it has them, or if forced`() {
        needsColorUpdate(cover(favorite = true, vibrant = true), onlyDominantColor = false) shouldBe true
        needsColorUpdate(cover(favorite = true, dominant = true), onlyDominantColor = false) shouldBe true

        val withBoth = cover(favorite = true, dominant = true, vibrant = true)
        needsColorUpdate(withBoth, onlyDominantColor = false) shouldBe false
        needsColorUpdate(withBoth, onlyDominantColor = false, force = true) shouldBe true
    }

    // Mocked so that neither MangaCover's constructor (which needs Injekt) nor its shared color maps are touched
    private fun cover(favorite: Boolean, dominant: Boolean = false, vibrant: Boolean = false) = mockk<MangaCover> {
        every { isMangaFavorite } returns favorite
        every { dominantCoverColors } returns (1 to 2).takeIf { dominant }
        every { vibrantCoverColor } returns 3.takeIf { vibrant }
    }
}
