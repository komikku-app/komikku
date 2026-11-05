package mihon.domain.migration.models

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationFlagTest {

    @Test
    fun `move downloads uses an unused bit and is absent from saved masks`() {
        assertEquals(128, MigrationFlag.MOVE_DOWNLOADS.flag)
        assertFalse(MigrationFlag.MOVE_DOWNLOADS in MigrationFlag.fromBit(127))
        assertEquals(
            setOf(MigrationFlag.MOVE_DOWNLOADS),
            MigrationFlag.fromBit(MigrationFlag.MOVE_DOWNLOADS.flag),
        )
        val defaults = MigrationFlag.entries.toSet() - MigrationFlag.MOVE_DOWNLOADS
        assertFalse(MigrationFlag.MOVE_DOWNLOADS in defaults)
        assertTrue(MigrationFlag.REMOVE_DOWNLOAD in defaults)
    }
}
